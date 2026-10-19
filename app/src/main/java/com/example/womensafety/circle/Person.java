package com.example.womensafety.circle;

import androidx.annotation.NonNull;

/** Someone in the user's circle: either a guardian of hers, or someone she looks after. */
public final class Person {

    public final String uid;
    public final String name;
    public final String phone;
    public final long since;

    public Person(@NonNull String uid, String name, String phone, long since) {
        this.uid = uid;
        this.name = name == null || name.trim().isEmpty() ? "Unnamed" : name.trim();
        this.phone = phone == null ? "" : phone;
        this.since = since;
    }
}
