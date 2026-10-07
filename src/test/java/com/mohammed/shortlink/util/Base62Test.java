package com.mohammed.shortlink.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class Base62Test {
    @ParameterizedTest
    @CsvSource({"1,1", "9,9", "10,a", "35,z", "36,A", "61,Z", "62,10", "3843,ZZ", "3844,100"})
    void matchesKnownEncodings(long id, String code) {
        assertThat(Base62.encode(id)).isEqualTo(code);
        assertThat(Base62.decode(code)).isEqualTo(id);
    }

    @Test
    void supportsMaximumLongId() {
        String code = "aZl8N0y58M7";
        assertThat(Base62.encode(Long.MAX_VALUE)).isEqualTo(code);
        assertThat(Base62.decode(code)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void preservesCase() {
        assertThat(Base62.decode("a")).isEqualTo(10L);
        assertThat(Base62.decode("A")).isEqualTo(36L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonPositiveIds(long id) {
        assertThatIllegalArgumentException().isThrownBy(() -> Base62.encode(id));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "01", "000a", " ", "a b", "abc-", "abc_", "abc/", "é", "ZZZZZZZZZZZZ", "ZZZZZZZZZZZ", "aZl8N0y58M8"})
    void rejectsInvalidNonCanonicalOrOverflowingCodes(String code) {
        assertThatIllegalArgumentException().isThrownBy(() -> Base62.decode(code));
    }

    @Test
    void roundTripsAndMatchesAnIndependentReferenceAcrossTheIdRange() {
        Random random = new Random(62);
        for (int i = 0; i < 2000; i++) {
            long id = random.nextLong() & Long.MAX_VALUE;
            if (id == 0) id = 1;
            String code = Base62.encode(id);
            assertThat(Base62.decode(code)).isEqualTo(id);
            // BigInteger avoids relying on the production long-arithmetic decoder.
            BigInteger reference = BigInteger.ZERO;
            String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            for (char character : code.toCharArray()) {
                reference = reference.multiply(BigInteger.valueOf(62))
                        .add(BigInteger.valueOf(alphabet.indexOf(character)));
            }
            assertThat(reference).isEqualTo(BigInteger.valueOf(id));
        }
    }

    @Test
    void producesDistinctCodesAcrossBaseBoundaries() {
        Set<String> codes = new HashSet<>();
        for (long id = 1; id <= 10000; id++) {
            assertThat(codes.add(Base62.encode(id))).isTrue();
        }
    }
}
