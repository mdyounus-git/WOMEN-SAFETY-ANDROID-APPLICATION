package com.example.womensafety.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** A person who receives SMS alerts during SOS. Works without internet or the app installed. */
public final class EmergencyContact {

    private final String name;
    private final String phoneNumber;

    public EmergencyContact(@NonNull String name, @NonNull String phoneNumber) {
        this.name = name.trim();
        this.phoneNumber = phoneNumber.trim();
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** Phone number in international form, defaulting to India (+91) for 10-digit numbers. */
    @NonNull
    public String getDialableNumber() {
        return PhoneNumbers.toInternational(phoneNumber);
    }

    /** Stored as "name:phone" (same format as v5). */
    @NonNull
    public String toStorageString() {
        return name + ":" + phoneNumber;
    }

    @Nullable
    public static EmergencyContact fromStorageString(@Nullable String data) {
        if (data == null) return null;
        // Split on the last ':' so names containing a colon still parse.
        int split = data.lastIndexOf(':');
        if (split <= 0 || split == data.length() - 1) return null;
        return new EmergencyContact(data.substring(0, split), data.substring(split + 1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmergencyContact)) return false;
        EmergencyContact that = (EmergencyContact) o;
        return name.equals(that.name) && phoneNumber.equals(that.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, phoneNumber);
    }
}
