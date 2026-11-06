package com.uprfvx.romio.romhandlers;

/** Binary layout verified against the supplied 3.6 symbols and compiled Thumb code, not retail Emerald. */
public final class ModernEmeraldTradeData {
    private ModernEmeraldTradeData() {}
    public static final int TABLE = 0xBC5FF8;
    public static final int COUNT = 4;
    public static final int SIZE = 64;
    public static final int NICKNAME_LENGTH = 11;
    public static final int GIVEN = 12;
    public static final int IVS = 20; // 14..19 is the separate perfect-IV bank and remains untouched.
    public static final int PERSONALITY = 40;
    public static final int REQUESTED = 60;
    public static final int REQUEST_SPECIAL_POINTER = 0x236AD8;
    public static final int REQUEST_ROUTINE = 0x1F00000;
    // Thumb-1 source: /com/uprfvx/romio/patches/modernemerald_trade_request.s
    private static final byte[] REQUEST_CODE = java.util.HexFormat.of().parseHex(
            "30b50c4b00f015f8002811d004000a490d880b22504329005143084a80188918074b"
            + "00f006f8002801d1280000e0200030bd184789601f08ca67000218756e08415c0008");
    public static byte[] requestCode() { return REQUEST_CODE.clone(); }

    public static boolean validRequestPatch(byte[] image) {
        long pointer = word32(image, REQUEST_SPECIAL_POINTER);
        byte[] code = java.util.Arrays.copyOfRange(image, REQUEST_ROUTINE, REQUEST_ROUTINE + REQUEST_CODE.length);
        if (pointer == 0x081F6089L) {
            for (byte b : code) if ((b & 255) != 255) return false;
            return true;
        }
        return pointer == 0x08000001L + REQUEST_ROUTINE && java.util.Arrays.equals(code, REQUEST_CODE);
    }

    public static void patchRequestComparison(byte[] image) {
        long pointer = 0x08000001L + REQUEST_ROUTINE;
        for (int i = 0; i < 4; i++) image[REQUEST_SPECIAL_POINTER + i] = (byte) (pointer >>> (8 * i));
        System.arraycopy(REQUEST_CODE, 0, image, REQUEST_ROUTINE, REQUEST_CODE.length);
    }

    private static long word32(byte[] image, int offset) {
        return Integer.toUnsignedLong((image[offset] & 255) | ((image[offset + 1] & 255) << 8)
                | ((image[offset + 2] & 255) << 16) | ((image[offset + 3] & 255) << 24));
    }
    public record Text(int offset, int length, String replacement) {}
    public static final java.util.List<Text> FOLLOW_UP_TEXT = java.util.List.of(
            new Text(0x27425D, 96, "I will train my new Pokemon well!\nPlease take good care of yours!"),
            new Text(0x2608DF, 114, "I really wanted a Pokemon\nthat another Trainer caught.\nThank you for trading with me!"),
            new Text(0x2C6BBD, 70, "I am delighted with our trade!\nI will take good care of my Pokemon!"));

    public static int offset(int index) { return TABLE + index * SIZE; }

    public static int unownLetter(long personality) {
        return (int) (((personality & 3) | ((personality >>> 6) & 12)
                | ((personality >>> 12) & 48) | ((personality >>> 18) & 192)) % 28);
    }

    /** Change the letter while retaining the original nature and shiny status. Unown is genderless. */
    public static long unownPersonality(long original, long otId, int letter) {
        boolean shiny = shiny(original, otId);
        long nature = original % 25;
        for (long candidate = nature; candidate <= 0xFFFFFFFFL; candidate += 25) {
            if (unownLetter(candidate) == letter && shiny(candidate, otId) == shiny) return candidate;
        }
        throw new IllegalArgumentException("No Unown personality for letter " + letter);
    }

    private static boolean shiny(long personality, long otId) {
        return ((personality >>> 16) ^ (personality & 65535) ^ (otId >>> 16) ^ (otId & 65535)) < 8;
    }
}
