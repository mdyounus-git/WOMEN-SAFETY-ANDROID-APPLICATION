package com.example.womensafety;

import java.io.Serializable;

public class EmergencyContact implements Serializable {
    private String name;
    private String phoneNumber;

    public EmergencyContact(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String toStorageString() {
        return name + ":" + phoneNumber;
    }

    public static EmergencyContact fromStorageString(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        String[] parts = data.split(":");
        if (parts.length == 2) {
            return new EmergencyContact(parts[0], parts[1]);
        }
        return null;
    }
}