package com.uprfvx.random;

import com.uprfvx.random.randomizers.StarterRandomizer;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.romhandlers.*;
import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
class ModernEmeraldStarterSupportTest {
    @TempDir Path temp;

    private ModernEmeraldRomHandler load() {
        var handler = new ModernEmeraldRomHandler();
        assertTrue(handler.loadRom(System.getenv("MODERN_EMERALD_ROM")));
        return handler;
    }

    @Test void basicsFollowAllActualEvolutionMethodsAndKeepEveryQualifyingSpecies() throws Exception {
        var handler = load();
        byte[] image = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        Set<Integer> targets = ModernEmeraldStarterData.evolvedSpecies(image);
        assertEquals(217, targets.size());
        var groups = handler.getBasicStarterSelectionGroups();
        assertEquals(214, groups.size());
        Set<Integer> basics = new HashSet<>();
        groups.forEach(group -> group.forEach(mon -> basics.add(handler.getInternalId(mon))));
        assertEquals(217, basics.size());
        assertTrue(basics.containsAll(List.of(63, 129, 360, 413, 414, 415, 416, 426, 433, 435, 436,
                452, 453, 454, 455, 456, 459, 460, 461)));
        for (int id : List.of(2, 3, 25, 113, 122, 143, 185, 202, 412, 457, 458)) assertFalse(basics.contains(id));
        for (int id = 1; id <= 461; id++) {
            assertEquals(ModernEmeraldSpeciesCatalog.isEligible(id) && !targets.contains(id), basics.contains(id),
                    "Basic classification of internal ID " + id);
        }
        assertEquals(List.of(410, 417, 418, 419), groups.stream()
                .filter(group -> handler.getInternalId(group.getFirst()) == 410).findFirst().orElseThrow()
                .stream().map(handler::getInternalId).toList());
    }

    @Test void hundredsOfSeedsChooseThreeDistinctBasicGroupsAndRejectUnsupportedStarterOptions() {
        var handler = load();
        Set<Integer> seen = new HashSet<>();
        for (int seed = 0; seed < 600; seed++) {
            Settings settings = new Settings();
            settings.setStartersMod(Settings.StartersMod.RANDOM_BASIC);
            settings.setStartersTypeMod(Settings.StartersTypeMod.FIRE_WATER_GRASS);
            settings.setStartersNoLegendaries(true);
            settings.setRandomizeStartersHeldItems(true);
            settings.setRivalCarriesStarterThroughout(true);
            Settings safe = settings.forModernEmerald();
            assertEquals(Settings.StartersTypeMod.NONE, safe.getStartersTypeMod());
            assertFalse(safe.isStartersNoLegendaries());
            assertFalse(safe.isRandomizeStartersHeldItems());
            assertTrue(safe.isRivalCarriesStarterThroughout());
            new StarterRandomizer(handler, safe, new Random(seed)).randomizeStarters();
            Set<Integer> keys = new HashSet<>();
            for (Species starter : handler.getStarters()) {
                int id = handler.getInternalId(starter);
                seen.add(id);
                assertTrue(keys.add(ModernEmeraldSpeciesCatalog.selectionKey(id)));
            }
            assertEquals(3, keys.size());
        }
        assertTrue(seen.containsAll(List.of(63, 129, 360, 452, 453, 454, 455, 459, 460, 461)));
        for (var unsupported : List.of(Settings.StartersMod.CUSTOM, Settings.StartersMod.COMPLETELY_RANDOM,
                Settings.StartersMod.RANDOM_WITH_TWO_EVOLUTIONS)) {
            Settings settings = new Settings();
            settings.setStartersMod(unsupported);
            assertEquals(Settings.StartersMod.UNCHANGED, settings.forModernEmerald().getStartersMod());
        }
    }

    @Test void starterOnlySaveHasExactByteBoundaryReadableNamesAndRoundTrips() throws Exception {
        var handler = load();
        byte[] before = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        Settings settings = new Settings();
        settings.setRomName(handler.getROMName());
        settings.setStartersMod(Settings.StartersMod.RANDOM_BASIC);
        Path output = temp.resolve("starters.gba");
        var result = new GameRandomizer(settings, null, handler,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(OutputStream.nullOutputStream()), 20261106L);
        assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
        assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
        byte[] after = Files.readAllBytes(output);
        BitSet allowed = new BitSet(before.length);
        allowed.set(ModernEmeraldStarterData.STARTERS, ModernEmeraldStarterData.STARTERS + 6);
        for (var text : ModernEmeraldStarterData.TYPE_EXAMPLES) {
            allowed.set(text.offset() + 13, text.offset() + text.length());
            // The NPC now says "For example, Treecko...", not "your Treecko...".
            assertEquals(before[text.offset() + 18], after[text.offset() + 13]);
            assertEquals(255, after[text.offset() + text.length() - 1] & 255);
        }
        int changes = 0;
        for (int offset = 0; offset < before.length; offset++) {
            if (before[offset] != after[offset]) {
                changes++;
                assertTrue(allowed.get(offset), "Unrelated changed byte " + Integer.toHexString(offset));
            }
        }
        assertTrue(changes > 0);
        var opened = new RomOpener(3).openRomFile(output.toFile());
        assertTrue(opened.wasOpeningSuccessful());
        var loaded = assertInstanceOf(ModernEmeraldRomHandler.class, opened.getRomHandler());
        assertEquals(handler.getStarters().stream().map(handler::getInternalId).toList(),
                loaded.getStarters().stream().map(loaded::getInternalId).toList());
        assertEquals("Bulbasaur", loaded.getSpeciesForInternalId(1).getName());
        assertEquals("Treecko", loaded.getSpeciesForInternalId(277).getName());
        for (Species mon : loaded.getSpeciesInclFormes()) if (mon != null) {
            assertTrue(mon.getFullName().matches("[\\p{IsLatin}0-9 .’'♀♂-]+"), mon.getFullName());
        }
        Path unchanged = temp.resolve("unchanged.gba");
        assertTrue(loaded.saveRom(unchanged.toString(), 0, false));
        assertArrayEquals(after, Files.readAllBytes(unchanged));
        byte[] invalid = after.clone();
        invalid[ModernEmeraldStarterData.STARTERS] = 2; // Ivysaur is not basic.
        invalid[ModernEmeraldStarterData.STARTERS + 1] = 0;
        assertFalse(ModernEmeraldRomHandler.isSupportedImage(invalid));
    }

    @Test void storedFormsStayIdenticalInAllThreeNativeStarterSlotsAndReload() throws Exception {
        var handler = load();
        handler.setStarters(List.of(handler.getSpeciesForInternalId(417), handler.getSpeciesForInternalId(454),
                handler.getSpeciesForInternalId(459)));
        Path output = temp.resolve("forms.gba");
        assertTrue(handler.saveRom(output.toString(), 0, false));
        byte[] saved = Files.readAllBytes(output);
        assertEquals(417, ModernEmeraldStarterData.word(saved, ModernEmeraldStarterData.STARTERS));
        assertEquals(454, ModernEmeraldStarterData.word(saved, ModernEmeraldStarterData.STARTERS + 2));
        assertEquals(459, ModernEmeraldStarterData.word(saved, ModernEmeraldStarterData.STARTERS + 4));
        var reopened = new ModernEmeraldRomHandler();
        assertTrue(reopened.loadRom(output.toString()));
        assertEquals(List.of("Deoxys-Attack", "Ursaluna-Bloodmoon", "Articuno-Galarian"),
                reopened.getStarters().stream().map(Species::getFullName).toList());
        assertThrows(IllegalArgumentException.class, () -> handler.setStarters(List.of(
                handler.getSpeciesForInternalId(410), handler.getSpeciesForInternalId(417),
                handler.getSpeciesForInternalId(201))));
    }

    @Test void startersCombineWithTheRequestedWildSettingsAndPreserveNativeMoveAndEvolutionData() throws Exception {
        var handler = load();
        Settings settings = new Settings();
        settings.setRomName(handler.getROMName());
        settings.setStartersMod(Settings.StartersMod.RANDOM_BASIC);
        settings.setRandomizeWildPokemon(true);
        settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        settings.setCatchEmAllEncounters(true);
        Path output = temp.resolve("starters-and-wild.gba");
        var result = new GameRandomizer(settings, null, handler,
                ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(OutputStream.nullOutputStream()), 42);
        assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
        assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
        byte[] before = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        byte[] after = Files.readAllBytes(output);
        ModernEmeraldWildSupportTest.assertNativeRouteCoverage(after);
        assertArrayEquals(Arrays.copyOfRange(before, ModernEmeraldStarterData.EVOLUTIONS,
                ModernEmeraldStarterData.EVOLUTIONS + 462 * 64), Arrays.copyOfRange(after,
                ModernEmeraldStarterData.EVOLUTIONS, ModernEmeraldStarterData.EVOLUTIONS + 462 * 64));
        assertArrayEquals(Arrays.copyOfRange(before, ModernEmeraldRomHandler.MOVE_DATA,
                ModernEmeraldRomHandler.MOVE_DATA + 375 * 12), Arrays.copyOfRange(after,
                ModernEmeraldRomHandler.MOVE_DATA, ModernEmeraldRomHandler.MOVE_DATA + 375 * 12));
        assertTrue(ModernEmeraldRomHandler.isSupportedImage(after));
        BitSet allowed = new BitSet(after.length);
        for (int offset : ModernEmeraldRomHandler.writableOffsets()) allowed.set(offset);
        for (int offset = 0; offset < after.length; offset++) {
            if (before[offset] != after[offset]) assertTrue(allowed.get(offset), "Unexpected changed byte " + offset);
        }
    }
}
