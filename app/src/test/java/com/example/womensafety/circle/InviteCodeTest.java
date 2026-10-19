package com.example.womensafety.circle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InviteCodeTest {

    @Test
    public void acceptsTypedCodes() {
        assertEquals("ABCDEFGHJK", CircleRepository.parseCode("abcdefghjk"));
        assertEquals("ABCDEFGHJK", CircleRepository.parseCode("  ABCDEFGHJK "));
    }

    @Test
    public void rejectsWrongCodes() {
        assertNull(CircleRepository.parseCode(null));
        assertNull(CircleRepository.parseCode("ABC"));
        assertNull(CircleRepository.parseCode("ABCDEFGHI1")); // 'I' and '1' are not used
        assertNull(CircleRepository.parseCode("https://example.com"));
    }
}
