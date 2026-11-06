package com.uprfvx.romio.romhandlers;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModernEmeraldCatalogTest {
    @Test void physicalRosterAndWeightingAreCompleteAndUnambiguous() {
        var groups = ModernEmeraldSpeciesCatalog.selectionGroups();
        assertEquals(431, groups.size());
        var ids = groups.stream().flatMap(Collection::stream).toList();
        assertEquals(434, ids.size());
        assertEquals(434, new HashSet<>(ids).size());
        assertTrue(groups.contains(List.of(410, 417, 418, 419))); // One Deoxys chance, four form choices.
        for (int id : List.of(201, 453, 454, 144, 145, 146, 459, 460, 461)) {
            assertTrue(groups.contains(List.of(id)), "Expected independent group " + id);
        }
        for (int id : List.of(0, 252, 276, 457, 458, 462, 463, 489)) assertFalse(ids.contains(id));
        assertEquals(4, ModernEmeraldSpeciesCatalog.RUNTIME_FORMS.size());
    }

    @Test void everyPhysicalEncounterSlotHasOneBindingAndCoverageUsesNormalRoutes() {
        assertEquals(495, ModernEmeraldEncounterData.TABLES.size());
        Set<Integer> offsets = new HashSet<>();
        int[] routeSlots = new int[3];
        for (var table : ModernEmeraldEncounterData.TABLES) {
            assertTrue(table.offset() > 0 && table.count() > 0 && table.rate() > 0);
            for (int i = 0; i < table.count(); i++) assertTrue(offsets.add(table.speciesOffset(i)));
            if (table.routeCoverageMask() != 0) {
                assertTrue(table.mapIndex() >= 16 && table.mapIndex() <= 49);
                for (int mode = 0; mode < 3; mode++) {
                    if ((table.routeCoverageMask() & (1 << mode)) != 0) routeSlots[mode] += table.count();
                }
            }
        }
        assertEquals(4609, offsets.size());
        assertArrayEquals(new int[]{676, 676, 676}, routeSlots);
    }
}
