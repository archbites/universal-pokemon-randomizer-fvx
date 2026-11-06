package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.romio.RomOpener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ModernEmeraldSupportTest {
    @TempDir Path temp;

    @Test void expandedEmeraldCannotUseRetailOffsets() {
        byte[] rom = new byte[32 * 1024 * 1024];
        System.arraycopy("BPEE".getBytes(StandardCharsets.US_ASCII), 0, rom, 0xAC, 4);
        rom[0x170] = (byte) 432;
        rom[0x171] = 1;
        assertTrue(ModernEmeraldRomHandler.hasExpandedEmeraldHeader(rom));
        assertFalse(new Gen3RomHandler().detectRom(rom));
        assertFalse(ModernEmeraldRomHandler.isSupportedImage(rom));
        rom[0x170] = (byte) 386;
        assertFalse(ModernEmeraldRomHandler.hasExpandedEmeraldHeader(rom));
        assertTrue(new Gen3RomHandler().detectRom(rom));
        assertFalse(ModernEmeraldRomHandler.isSupportedImage(new byte[0]));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MODERN_EMERALD_ROM", matches = ".+")
    void verifiedReleaseOpensAndNoOpSaveIsByteIdentical() throws Exception {
        Path source = Path.of(System.getenv("MODERN_EMERALD_ROM"));
        var result = new RomOpener(3).openRomFile(source.toFile());
        assertTrue(result.wasOpeningSuccessful());
        var handler = assertInstanceOf(ModernEmeraldRomHandler.class, result.getRomHandler());
        assertEquals("Pokemon Modern Emerald 3.6", handler.getROMName());
        assertTrue(handler.isRomValid(null));
        assertEquals(375, handler.getMoves().size());
        assertEquals(428, handler.getSpecies().size());
        assertEquals(435, handler.getSpeciesInclFormes().size());
        assertEquals(7, handler.getAltFormes().size());
        assertEquals(431, handler.getWildSelectionGroups().size());
        assertEquals(28, handler.getSpeciesForInternalId(201).getCosmeticFormeNumbers().size());
        assertEquals(java.util.List.of("TREECKO", "TORCHIC", "MUDKIP"),
                handler.getStarters().stream().map(s -> s.getName().toUpperCase()).toList());
        Path output = temp.resolve("unchanged.gba");
        assertTrue(handler.saveRom(output.toString(), 1, false));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(output));

        byte[] corrupted = Files.readAllBytes(source);
        corrupted[0x1BC] ^= 1;
        assertFalse(ModernEmeraldRomHandler.isSupportedImage(corrupted));
        Path unknown = temp.resolve("unknown.gba");
        Files.write(unknown, corrupted);
        assertEquals(RomOpener.FailType.UNSUPPORTED_ROM, new RomOpener(3).openRomFile(unknown.toFile()).getFailType());
    }
}
