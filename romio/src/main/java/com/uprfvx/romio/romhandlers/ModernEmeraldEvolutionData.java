package com.uprfvx.romio.romhandlers;

import java.util.*;

/** Read-only evolution rules from the actual 3.6 binary. Never infer missing canonical links. */
public final class ModernEmeraldEvolutionData {
    private ModernEmeraldEvolutionData() {}
    private static final int SPECIES_INFO = 0x8E5960;
    private static final Set<Integer> LEVEL_METHODS = Set.of(4, 8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19, 24, 25);

    public record Link(int from, int to, int method, int parameter, int estimatedLevel) {
        public boolean hasLevelThreshold() { return LEVEL_METHODS.contains(method); }
        public boolean requiresLevelUp() { return method != 5 && method != 6 && method != 7; }
    }

    public record Policy(List<Link> links, Map<Integer, Integer> minimumLevels) {
        public Policy {
            links = List.copyOf(links);
            minimumLevels = Map.copyOf(minimumLevels);
        }
        public int minimumLevel(int internalId) {
            Integer level = minimumLevels.get(internalId);
            if (level == null) throw new IllegalArgumentException("Unknown Modern Emerald species " + internalId);
            return level;
        }
    }

    public static List<Link> readLinks(byte[] image) {
        List<Link> links = new ArrayList<>();
        for (var group : ModernEmeraldSpeciesCatalog.selectionGroups()) for (int from : group) {
            for (int slot = 0; slot < 8; slot++) {
                int offset = ModernEmeraldStarterData.EVOLUTIONS + from * 64 + slot * 8;
                int method = ModernEmeraldStarterData.word(image, offset);
                if (method == 0) continue;
                int parameter = ModernEmeraldStarterData.word(image, offset + 2);
                int to = ModernEmeraldStarterData.word(image, offset + 4);
                if (method > 26 || !ModernEmeraldSpeciesCatalog.isEligible(to)) {
                    throw new IllegalArgumentException("Unknown Modern Emerald evolution at " + Integer.toHexString(offset));
                }
                if (LEVEL_METHODS.contains(method) && (parameter < 1 || parameter > 100)) {
                    throw new IllegalArgumentException("Invalid evolution level " + parameter);
                }
                links.add(new Link(from, to, method, parameter, LEVEL_METHODS.contains(method) ? parameter : 0));
            }
        }
        return List.copyOf(links);
    }

    public static Policy read(byte[] image) {
        List<Link> raw = readLinks(image);
        List<int[]> samples = raw.stream().filter(Link::hasLevelThreshold)
                .map(link -> new int[]{bst(image, link.from()), bst(image, link.to()), link.parameter()}).toList();
        List<Link> estimated = new ArrayList<>();
        for (Link link : raw) {
            int level = link.hasLevelThreshold() ? link.parameter()
                    : AbstractRomHandler.findEvolutionLevel(samples, bst(image, link.from()), bst(image, link.to()));
            estimated.add(new Link(link.from(), link.to(), link.method(), link.parameter(), Math.clamp(level, 1, 100)));
        }
        // Preserve the shared estimator's stage spacing for non-level evolutions. Use the cheapest
        // incoming path rather than depending on whichever predecessor happened to be listed first.
        for (int i = 0; i < estimated.size(); i++) {
            Link link = estimated.get(i);
            if (link.hasLevelThreshold()) continue;
            int level = link.estimatedLevel();
            int previous = estimated.stream().filter(e -> e.to() == link.from())
                    .mapToInt(Link::estimatedLevel).min().orElse(0);
            if (previous > 0) level = Math.max(level, (int) Math.ceil(previous * 1.25));
            for (Link next : estimated) if (next.from() == link.to()) {
                level = Math.min(level, (int) Math.ceil(next.estimatedLevel() * 0.8));
            }
            estimated.set(i, new Link(link.from(), link.to(), link.method(), link.parameter(), Math.clamp(level, 1, 100)));
        }
        Map<Integer, List<Link>> incoming = new HashMap<>();
        estimated.forEach(link -> incoming.computeIfAbsent(link.to(), _ -> new ArrayList<>()).add(link));
        Map<Integer, Integer> minimum = new HashMap<>();
        for (var group : ModernEmeraldSpeciesCatalog.selectionGroups()) for (int id : group) {
            resolve(id, incoming, minimum, new HashSet<>());
        }
        return new Policy(estimated, minimum);
    }

    private static int resolve(int id, Map<Integer, List<Link>> incoming, Map<Integer, Integer> minimum, Set<Integer> visiting) {
        if (minimum.containsKey(id)) return minimum.get(id);
        if (!visiting.add(id)) throw new IllegalArgumentException("Cyclic Modern Emerald evolution path at " + id);
        int level = 1;
        if (incoming.containsKey(id)) {
            level = Integer.MAX_VALUE;
            for (Link link : incoming.get(id)) {
                int predecessor = resolve(link.from(), incoming, minimum, visiting);
                level = Math.min(level, Math.max(link.estimatedLevel(), predecessor + (link.requiresLevelUp() ? 1 : 0)));
            }
        }
        visiting.remove(id);
        minimum.put(id, level);
        return level;
    }

    private static int bst(byte[] image, int id) {
        int total = 0;
        for (int stat = 0; stat < 6; stat++) total += image[SPECIES_INFO + id * 40 + stat] & 255;
        return total;
    }
}
