package com.uprfvx.random;

import com.uprfvx.romio.romhandlers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
class ModernEmeraldRivalSupportTest {
    @TempDir Path temp;
    private ModernEmeraldRomHandler load() {
        var h = new ModernEmeraldRomHandler();
        assertTrue(h.loadRom(System.getenv("MODERN_EMERALD_ROM")));
        return h;
    }
    @Test void allThirtySlotsUseTheRightStarterLineAndOnlyVerifiedFieldsChange() throws Exception {
        var h = load();
        h.setStarters(List.of(h.getSpeciesForInternalId(1), h.getSpeciesForInternalId(4), h.getSpeciesForInternalId(7)));
        h.updateRivalStarters(new Random(1));
        Path output = temp.resolve("rivals.gba");
        assertTrue(h.saveRom(output.toString(), 0, false));
        byte[] before = Files.readAllBytes(Path.of(System.getenv("MODERN_EMERALD_ROM")));
        byte[] after = Files.readAllBytes(output);
        BitSet allowed = new BitSet(after.length);
        allowed.set(ModernEmeraldStarterData.STARTERS, ModernEmeraldStarterData.STARTERS + 6);
        for (var t : ModernEmeraldStarterData.TYPE_EXAMPLES) allowed.set(t.offset() + 13, t.offset() + t.length());
        assertEquals(30, ModernEmeraldRivalData.BINDINGS.size());
        int opening = 0;
        for (var b : ModernEmeraldRivalData.BINDINGS) {
            int level = after[b.levelOffset()] & 255;
            int base = 1 + b.starterIndex() * 3;
            int expected = level < 16 ? base : level < (base == 1 ? 32 : 36) ? base + 1 : base + 2;
            assertEquals(expected, ModernEmeraldStarterData.word(after, b.speciesOffset()), b.name());
            allowed.set(b.speciesOffset(), b.speciesOffset() + 2);
            if (b.customFlagOffset() != 0) {
                opening++;
                assertEquals(0, after[b.customFlagOffset()]); // native learnset for the replacement
                assertEquals(1, before[b.customFlagOffset()]);
                assertEquals(1, after[b.customFlagOffset() + 32]); // one-mon party, so switching stride is safe
                allowed.set(b.customFlagOffset());
            }
        }
        assertEquals(6, opening);
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) {
            assertTrue(allowed.get(i), "Unrelated write " + Integer.toHexString(i));
        }
        var reopened = new ModernEmeraldRomHandler();
        assertTrue(reopened.loadRom(output.toString()));
        assertEquals(h.getRivalStarterAssignments(), reopened.getRivalStarterAssignments());
        Path unchanged = temp.resolve("unchanged.gba");
        assertTrue(reopened.saveRom(unchanged.toString(), 0, false));
        assertArrayEquals(after, Files.readAllBytes(unchanged));
    }

    @Test void branchingEvolutionIsConsistentForBothGendersAndPhysicalFormsPersist() throws Exception {
        var h = load();
        h.setStarters(List.of(h.getSpeciesForInternalId(133), h.getSpeciesForInternalId(301), h.getSpeciesForInternalId(419)));
        for (int seed = 0; seed < 12; seed++) {
            h.updateRivalStarters(new Random(seed));
            Path output = temp.resolve("branches.gba");
            assertTrue(h.saveRom(output.toString(), 0, false));
            byte[] image = Files.readAllBytes(output);
            Set<Integer> eeveeFinals = new HashSet<>();
            for (var b : ModernEmeraldRivalData.BINDINGS) {
                int id = ModernEmeraldStarterData.word(image, b.speciesOffset());
                int level = image[b.levelOffset()] & 255;
                if (b.starterIndex() == 2) assertEquals(419, id);
                if (b.starterIndex() == 1) assertEquals(level < 20 ? 301 : 302, id); // Ninjask; no bonus Shedinja party slot
                if (b.starterIndex() == 0) {
                    if (level < 36) assertEquals(133, id);
                    else { assertNotEquals(133, id); eeveeFinals.add(id); }
                }
            }
            assertEquals(1, eeveeFinals.size());
        }
    }

    @Test void requestedCombinedPipelineRetainsOptionWritesAllBattlesAndLogsThem() throws Exception {
        var h = load();
        Settings s = new Settings();
        s.setRomName(h.getROMName());
        s.setStartersMod(Settings.StartersMod.RANDOM_BASIC);
        s.setRivalCarriesStarterThroughout(true);
        s.setRandomizeWildPokemon(true);
        s.setCatchEmAllEncounters(true);
        s.setBanPrematureEvos(true);
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        s.setRandomizeInGameTradesNicknames(true);
        s.setRandomizeInGameTradesIVs(true);
        assertTrue(s.forModernEmerald().isRivalCarriesStarterThroughout());
        Path output = temp.resolve("combined.gba");
        var log = new ByteArrayOutputStream();
        var result = new GameRandomizer(s, null, h, ResourceBundle.getBundle("com.uprfvx.random.gui.Bundle"), false)
                .randomize(output.toString(), new PrintStream(log), 20261106);
        assertTrue(result.wasSaveSuccessful(), () -> result.getException().toString());
        assertTrue(result.wasLogSuccessful(), () -> result.getLogException().toString());
        assertTrue(log.toString().contains("sParty_BrendanRoute103Treecko"));
        assertTrue(log.toString().contains("sParty_MayLilycoveTorchic"));
        byte[] image = Files.readAllBytes(output);
        ModernEmeraldEvolutionSupportTest.assertNativeCoverage(image);
        for (var b : ModernEmeraldRivalData.BINDINGS) if ((image[b.levelOffset()] & 255) == 5) {
            assertEquals(h.getInternalId(h.getStarters().get(b.starterIndex())), ModernEmeraldStarterData.word(image, b.speciesOffset()));
        }
        assertTrue(ModernEmeraldRomHandler.isSupportedImage(image));
    }
}
