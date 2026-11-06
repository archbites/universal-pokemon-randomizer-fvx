package com.uprfvx.romio.romhandlers;

import java.util.*;

/** The three shared starter choices and read-only basic-stage classification for Modern Emerald 3.6. */
public final class ModernEmeraldStarterData {
    private ModernEmeraldStarterData() {}
    public static final int STARTERS = 0x92EB54;
    public static final int EVOLUTIONS = 0x8F0100;
    public record Text(int offset, int length) {}
    public static final List<Text> TYPE_EXAMPLES = List.of(
            new Text(0x26491C, 133), new Text(0x2649A1, 130), new Text(0x264A23, 138));

    /** Follow all eight evolution records, including hack-specific methods, without rewriting the graph. */
    public static Set<Integer> evolvedSpecies(byte[] image) {
        Set<Integer> evolved = new HashSet<>();
        for (int internal = 1; internal <= 461; internal++) {
            if (!ModernEmeraldSpeciesCatalog.isEligible(internal)) continue;
            for (int slot = 0; slot < 8; slot++) {
                int offset = EVOLUTIONS + internal * 64 + slot * 8;
                if (word(image, offset) == 0) continue;
                int target = word(image, offset + 4);
                if (!ModernEmeraldSpeciesCatalog.isEligible(target)) {
                    throw new IllegalArgumentException("Invalid Modern Emerald evolution target " + target);
                }
                evolved.add(target);
            }
        }
        return Collections.unmodifiableSet(evolved);
    }

    public static List<List<Integer>> basicGroups(byte[] image) {
        Set<Integer> evolved = evolvedSpecies(image);
        return ModernEmeraldSpeciesCatalog.selectionGroups().stream()
                .map(group -> group.stream().filter(id -> !evolved.contains(id)).toList())
                .filter(group -> !group.isEmpty()).toList();
    }

    public static int word(byte[] image, int offset) {
        return (image[offset] & 255) | ((image[offset + 1] & 255) << 8);
    }

    /** Keep the NPC's true type examples, but remove "your" when they no longer describe the player's starter. */
    public static void makeTypeExamplesGeneric(byte[] image) {
        byte[] your = {(byte) 0xED, (byte) 0xE3, (byte) 0xE9, (byte) 0xE6, 0};
        for (Text text : TYPE_EXAMPLES) {
            int offset = text.offset() + 13; // "For example, " in the original GBA text.
            if (!Arrays.equals(your, Arrays.copyOfRange(image, offset, offset + your.length))) continue;
            System.arraycopy(image, offset + your.length, image, offset, text.length() - 13 - your.length);
            Arrays.fill(image, text.offset() + text.length() - your.length,
                    text.offset() + text.length(), (byte) 0xFF);
        }
    }
}
