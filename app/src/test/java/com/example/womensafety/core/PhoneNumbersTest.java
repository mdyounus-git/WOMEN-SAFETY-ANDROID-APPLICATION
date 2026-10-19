package com.example.womensafety.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneNumbersTest {

    @Test
    public void acceptsIndianMobileFormats() {
        assertTrue(PhoneNumbers.isValid("9876543210"));
        assertTrue(PhoneNumbers.isValid("98765 43210"));
        assertTrue(PhoneNumbers.isValid("09876543210"));
        assertTrue(PhoneNumbers.isValid("919876543210"));
        assertTrue(PhoneNumbers.isValid("+91 98765-43210"));
        assertTrue(PhoneNumbers.isValid("+447700900123"));
    }

    @Test
    public void rejectsBadNumbers() {
        assertFalse(PhoneNumbers.isValid(""));
        assertFalse(PhoneNumbers.isValid("12345"));
        assertFalse(PhoneNumbers.isValid("1234567890")); // Indian mobiles start with 6-9
        assertFalse(PhoneNumbers.isValid("98765432101234"));
    }

    @Test
    public void convertsToInternational() {
        assertEquals("+919876543210", PhoneNumbers.toInternational("98765 43210"));
        assertEquals("+919876543210", PhoneNumbers.toInternational("09876543210"));
        assertEquals("+919876543210", PhoneNumbers.toInternational("919876543210"));
        assertEquals("+447700900123", PhoneNumbers.toInternational("+44 7700 900123"));
    }

    @Test
    public void contactStorageRoundTrips() {
        EmergencyContact c = new EmergencyContact("Ammi: home", "9876543210");
        EmergencyContact back = EmergencyContact.fromStorageString(c.toStorageString());
        assertEquals(c, back);
        assertEquals("Ammi: home", back.getName());
        assertNull(EmergencyContact.fromStorageString("no-separator"));
        assertNull(EmergencyContact.fromStorageString("name:"));
        assertNull(EmergencyContact.fromStorageString(null));
    }
}
