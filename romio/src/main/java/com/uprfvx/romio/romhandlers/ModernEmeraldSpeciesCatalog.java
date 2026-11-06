package com.uprfvx.romio.romhandlers;

import java.util.*;
import java.util.stream.IntStream;

/** Physical identities and selection policy for Modern Emerald 3.6, independent of storage or dex order. */
public final class ModernEmeraldSpeciesCatalog {
    private ModernEmeraldSpeciesCatalog() {}

    public record Form(int internalId, int baseInternalId, int formNumber, String suffix, int generation) {}

    public static final List<Form> FORMS = List.of(
            new Form(417, 410, 1, "-Attack", 3),
            new Form(418, 410, 2, "-Defense", 3),
            new Form(419, 410, 3, "-Speed", 3),
            new Form(454, 453, 1, "-Bloodmoon", 9),
            new Form(459, 144, 1, "-Galarian", 8),
            new Form(460, 145, 1, "-Galarian", 8),
            new Form(461, 146, 1, "-Galarian", 8));

    public static boolean isEligible(int internalId) {
        // Old Unown graphics placeholders are not actual species. 458 is a debug mon named "Unown".
        return internalId >= 1 && internalId <= 461
                && !(internalId >= 252 && internalId <= 276) && internalId != 457 && internalId != 458;
    }

    public static int selectionKey(int internalId) {
        if (!isEligible(internalId)) throw new IllegalArgumentException("Ineligible species: " + internalId);
        return internalId >= 417 && internalId <= 419 ? 410 : internalId;
    }

    /** 431 equally weighted groups, containing 434 real species/form records. */
    public static List<List<Integer>> selectionGroups() {
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        IntStream.rangeClosed(1, 461).filter(ModernEmeraldSpeciesCatalog::isEligible).forEach(id ->
                groups.computeIfAbsent(selectionKey(id), _ -> new ArrayList<>()).add(id));
        return groups.values().stream().map(List::copyOf).toList();
    }

    /** These have one physical species ID. Their appearances/types are selected by the game at runtime. */
    public static final Map<Integer, String> RUNTIME_FORMS = Map.of(
            201, "28 Unown letters from personality; never use the old-Unown or debug species IDs",
            308, "Spinda spots from personality",
            385, "Castform weather transformations",
            413, "Arceus types from held items/Multitype");
}
