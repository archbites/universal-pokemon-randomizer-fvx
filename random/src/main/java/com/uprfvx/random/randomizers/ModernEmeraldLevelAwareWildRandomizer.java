package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.*;

import java.util.*;

/** Cover difficult groups at legal levels first, then fill every remaining physical slot independently. */
final class ModernEmeraldLevelAwareWildRandomizer {
    private ModernEmeraldLevelAwareWildRandomizer() {}

    static void randomize(ModernEmeraldRomHandler handler, boolean catchEmAll, Random random) {
        var areas = handler.getEncounters(false);
        var groups = handler.getWildSelectionGroups();
        int[] minimum = groups.stream().mapToInt(group -> group.stream()
                .mapToInt(handler::getMinimumEvolutionLevel).min().orElseThrow()).toArray();
        SortedMap<Integer, List<Encounter>> anchors = new TreeMap<>(Comparator
                .<Integer>comparingInt(Integer::bitCount).reversed().thenComparingInt(Integer::intValue));
        Map<Integer, List<Encounter>> routes = new TreeMap<>(anchors.comparator());
        List<Encounter> all = new ArrayList<>();
        for (int a = 0; a < areas.size(); a++) {
            int mask = handler.getLevelCoverageMask(ModernEmeraldEncounterData.TABLES.get(a));
            if (mask != 0) anchors.computeIfAbsent(mask, _ -> new ArrayList<>()).addAll(areas.get(a));
            int routeMask = ModernEmeraldEncounterData.TABLES.get(a).routeCoverageMask();
            if (routeMask != 0) routes.computeIfAbsent(routeMask, _ -> new ArrayList<>()).addAll(areas.get(a));
            all.addAll(areas.get(a));
        }
        // Encounter is mutable and has value equality: assigned slots must be tracked by identity.
        Set<Encounter> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Set<Integer>> covered = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        if (catchEmAll) {
            // Keep every basic choice on ordinary routes so no family requires a postgame area.
            cover(handler, groups, minimum, routes, covered, assigned, true, random);
            for (int mode = 0; mode < 3; mode++) for (int group = 0; group < groups.size(); group++) {
                if (minimum[group] == 1 && !covered.get(mode).contains(group)) {
                    throw new IllegalStateException("Modern Emerald basic route coverage incomplete for mode " + mode);
                }
            }
            cover(handler, groups, minimum, anchors, covered, assigned, false, random);
            for (int mode = 0; mode < 3; mode++) if (covered.get(mode).size() != groups.size()) {
                throw new IllegalStateException("No premature evolutions / Catch Em All coverage incomplete for mode " + mode);
            }
        }
        Map<Integer, List<Integer>> eligibleByLevel = new HashMap<>();
        for (Encounter slot : all) if (!assigned.contains(slot)) {
            var eligible = eligibleByLevel.computeIfAbsent(slot.getLevel(), level -> {
                List<Integer> choices = new ArrayList<>();
                for (int group = 0; group < groups.size(); group++) if (minimum[group] <= level) choices.add(group);
                return choices;
            });
            int group = eligible.get(random.nextInt(eligible.size()));
            setForm(handler, slot, groups.get(group), random);
        }
        handler.setEncounters(false, areas);
    }

    private static void cover(ModernEmeraldRomHandler handler, List<List<Species>> groups, int[] minimum,
                              Map<Integer, List<Encounter>> anchors, List<Set<Integer>> covered,
                              Set<Encounter> assigned, boolean basicsOnly, Random random) {
        for (var entry : anchors.entrySet()) {
            int mask = entry.getKey();
            List<Encounter> slots = new ArrayList<>(entry.getValue().stream().filter(e -> !assigned.contains(e)).toList());
            Collections.shuffle(slots, random);
            slots.sort(Comparator.comparingInt(Encounter::getLevel));
            List<Integer> missing = new ArrayList<>();
            for (int group = 0; group < groups.size(); group++) {
                if (basicsOnly && minimum[group] != 1) continue;
                for (int mode = 0; mode < 3; mode++) if ((mask & (1 << mode)) != 0 && !covered.get(mode).contains(group)) {
                    missing.add(group);
                    break;
                }
            }
            Collections.shuffle(missing, random);
            missing.sort(Comparator.<Integer>comparingInt(g -> minimum[g]).reversed());
            for (int group : missing) {
                int slot = 0;
                while (slot < slots.size() && slots.get(slot).getLevel() < minimum[group]) slot++;
                if (slot == slots.size()) continue; // Another coverage mask may supply a legal slot.
                Encounter target = slots.remove(slot);
                setForm(handler, target, groups.get(group), random);
                assigned.add(target);
                for (int mode = 0; mode < 3; mode++) if ((mask & (1 << mode)) != 0) covered.get(mode).add(group);
            }
        }
    }

    private static void setForm(ModernEmeraldRomHandler handler, Encounter slot, List<Species> group, Random random) {
        var forms = group.stream().filter(mon -> handler.getMinimumEvolutionLevel(mon) <= slot.getLevel()).toList();
        slot.getSpeciesHolder().setSpecies(forms.get(random.nextInt(forms.size())));
    }
}
