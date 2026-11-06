package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.*;
import com.uprfvx.romio.romhandlers.ModernEmeraldEncounterData;
import com.uprfvx.romio.romhandlers.ModernEmeraldRomHandler;

import java.util.*;

/** Maximum replacements, unrestricted types/stages, with coverage independent of the in-game encounter mode. */
final class ModernEmeraldWildRandomizer {
    private ModernEmeraldWildRandomizer() {}

    static void randomize(ModernEmeraldRomHandler handler, boolean catchEmAll, Random random) {
        List<EncounterArea> areas = handler.getEncounters(false);
        List<List<Species>> groups = handler.getWildSelectionGroups();
        Map<Integer, List<Encounter>> slotsByMask = new TreeMap<>(Comparator
                .<Integer>comparingInt(Integer::bitCount).reversed().thenComparingInt(Integer::intValue));
        for (int area = 0; area < areas.size(); area++) {
            int mask = ModernEmeraldEncounterData.TABLES.get(area).routeCoverageMask();
            slotsByMask.computeIfAbsent(mask, _ -> new ArrayList<>()).addAll(areas.get(area));
        }
        List<Set<Integer>> covered = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        for (var entry : slotsByMask.entrySet()) {
            int mask = entry.getKey();
            List<Encounter> slots = entry.getValue();
            Collections.shuffle(slots, random);
            List<Integer> bag = new ArrayList<>();
            if (catchEmAll) {
                // Shared route slots are placed first. Day and night then fill their own missing groups.
                for (int group = 0; group < groups.size(); group++) {
                    for (int mode = 0; mode < 3; mode++) {
                        if ((mask & (1 << mode)) != 0 && !covered.get(mode).contains(group)) {
                            bag.add(group);
                            break;
                        }
                    }
                }
            }
            for (Encounter slot : slots) {
                int group;
                if (catchEmAll) {
                    if (bag.isEmpty()) for (int i = 0; i < groups.size(); i++) bag.add(i);
                    group = bag.remove(random.nextInt(bag.size()));
                } else group = random.nextInt(groups.size());
                List<Species> forms = groups.get(group);
                slot.getSpeciesHolder().setSpecies(forms.get(random.nextInt(forms.size())));
                for (int mode = 0; mode < 3; mode++) {
                    if ((mask & (1 << mode)) != 0) covered.get(mode).add(group);
                }
            }
        }
        if (catchEmAll) {
            for (int mode = 0; mode < 3; mode++) {
                if (covered.get(mode).size() != groups.size()) {
                    throw new IllegalStateException("Modern Emerald coverage incomplete for mode " + mode);
                }
            }
        }
        handler.setEncounters(false, areas);
    }
}
