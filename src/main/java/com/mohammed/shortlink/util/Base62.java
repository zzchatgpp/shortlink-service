package com.mohammed.shortlink.util;

/** Converts positive database IDs to canonical, case-sensitive Base62 codes. */
public final class Base62 {
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();
    private static final int MAX_CODE_LENGTH = 11;

    private Base62() {
        // Utility class; no instances needed.
    }

    public static String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }

        StringBuilder code = new StringBuilder(MAX_CODE_LENGTH);
        while (id > 0) {
            int remainder = (int) (id % BASE);
            code.append(ALPHABET.charAt(remainder));
            id /= BASE;
        }
        return code.reverse().toString();
    }

    public static long decode(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Code must contain 1 to 11 Base62 characters");
        }
        if (code.charAt(0) == '0') {
            throw new IllegalArgumentException("Code must represent a positive ID without leading zeros");
        }

        long id = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Code contains an invalid Base62 character");
            }
            // Check before multiplying so overflowing values cannot wrap around.
            if (id > (Long.MAX_VALUE - digit) / BASE) {
                throw new IllegalArgumentException("Code exceeds the supported ID range");
            }
            id = id * BASE + digit;
        }
        return id;
    }
}
