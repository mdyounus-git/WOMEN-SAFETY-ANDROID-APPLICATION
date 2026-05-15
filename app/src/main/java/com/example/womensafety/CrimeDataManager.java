package com.example.womensafety;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Crime Data Manager
 * Manages pre-loaded crime hotspot database for major Indian cities
 */
public class CrimeDataManager {
    
    private static final String TAG = "CrimeDataManager";
    private static List<CrimeHotspot> crimeDatabase;
    
    public static class CrimeHotspot {
        public double latitude;
        public double longitude;
        public String type; // "theft", "assault", "harassment", "robbery"
        public String severity; // "low", "medium", "high"
        public long timestamp;
        public String location;
        
        public CrimeHotspot(double lat, double lon, String type, String severity, 
                           long timestamp, String location) {
            this.latitude = lat;
            this.longitude = lon;
            this.type = type;
            this.severity = severity;
            this.timestamp = timestamp;
            this.location = location;
        }
    }
    
    /**
     * Initialize crime database
     */
    public static void initialize(Context context) {
        if (crimeDatabase != null) return;
        
        Log.d(TAG, "📊 Initializing crime database...");
        crimeDatabase = new ArrayList<>();
        
        // Load pre-defined crime hotspots for major Indian cities
        loadBangaloreData();
        loadDelhiData();
        loadMumbaiData();
        loadHyderabadData();
        
        Log.d(TAG, "✅ Loaded " + crimeDatabase.size() + " crime hotspots");
    }
    
    /**
     * Get nearby crime hotspots within radius (km)
     */
    public static List<CrimeHotspot> getNearbyHotspots(double lat, double lon, double radiusKm) {
        if (crimeDatabase == null) {
            return new ArrayList<>();
        }
        
        List<CrimeHotspot> nearby = new ArrayList<>();
        
        for (CrimeHotspot hotspot : crimeDatabase) {
            double distance = calculateDistance(lat, lon, hotspot.latitude, hotspot.longitude);
            if (distance <= radiusKm) {
                nearby.add(hotspot);
            }
        }
        
        return nearby;
    }
    
    /**
     * Add user-reported incident (for future crowdsourcing feature)
     */
    public static void addIncident(double lat, double lon, String type, String description) {
        if (crimeDatabase == null) {
            crimeDatabase = new ArrayList<>();
        }
        
        CrimeHotspot incident = new CrimeHotspot(
            lat, lon, type, "medium", 
            System.currentTimeMillis(), 
            description
        );
        
        crimeDatabase.add(incident);
        Log.d(TAG, "✅ Added user-reported incident: " + type + " at " + description);
    }
    
    // ==================== CITY DATA ====================
    
    /**
     * Bangalore Crime Hotspots
     */
    private static void loadBangaloreData() {
        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;
        
        // Koramangala area
        crimeDatabase.add(new CrimeHotspot(
            12.9352, 77.6245, "theft", "medium",
            now - (2 * oneDay), "Koramangala 5th Block"
        ));
        
        // Whitefield area
        crimeDatabase.add(new CrimeHotspot(
            12.9698, 77.7499, "harassment", "low",
            now - (5 * oneDay), "Whitefield Main Road"
        ));
        
        // Indiranagar
        crimeDatabase.add(new CrimeHotspot(
            12.9716, 77.6412, "theft", "high",
            now - oneDay, "Indiranagar 100 Feet Road"
        ));
        
        // Electronic City
        crimeDatabase.add(new CrimeHotspot(
            12.8458, 77.6603, "robbery", "medium",
            now - (3 * oneDay), "Electronic City Phase 1"
        ));
        
        // Marathahalli
        crimeDatabase.add(new CrimeHotspot(
            12.9591, 77.6974, "theft", "medium",
            now - (4 * oneDay), "Marathahalli Bridge"
        ));
        
        // JP Nagar
        crimeDatabase.add(new CrimeHotspot(
            12.9081, 77.5855, "harassment", "low",
            now - (7 * oneDay), "JP Nagar 7th Phase"
        ));
        
        // BTM Layout
        crimeDatabase.add(new CrimeHotspot(
            12.9165, 77.6101, "theft", "high",
            now - (2 * oneDay), "BTM 2nd Stage"
        ));
    }
    
    /**
     * Delhi Crime Hotspots
     */
    private static void loadDelhiData() {
        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;
        
        // Connaught Place
        crimeDatabase.add(new CrimeHotspot(
            28.6315, 77.2167, "theft", "high",
            now - oneDay, "Connaught Place"
        ));
        
        // Karol Bagh
        crimeDatabase.add(new CrimeHotspot(
            28.6519, 77.1909, "robbery", "high",
            now - (2 * oneDay), "Karol Bagh Market"
        ));
        
        // Saket
        crimeDatabase.add(new CrimeHotspot(
            28.5244, 77.2066, "harassment", "medium",
            now - (3 * oneDay), "Saket District Center"
        ));
        
        // Dwarka
        crimeDatabase.add(new CrimeHotspot(
            28.5921, 77.0460, "theft", "low",
            now - (5 * oneDay), "Dwarka Sector 10"
        ));
        
        // Rohini
        crimeDatabase.add(new CrimeHotspot(
            28.7499, 77.0674, "assault", "medium",
            now - (4 * oneDay), "Rohini Sector 15"
        ));
    }
    
    /**
     * Mumbai Crime Hotspots
     */
    private static void loadMumbaiData() {
        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;
        
        // Dadar
        crimeDatabase.add(new CrimeHotspot(
            19.0183, 72.8458, "theft", "high",
            now - oneDay, "Dadar Station"
        ));
        
        // Andheri
        crimeDatabase.add(new CrimeHotspot(
            19.1136, 72.8697, "harassment", "medium",
            now - (3 * oneDay), "Andheri West"
        ));
        
        // Bandra
        crimeDatabase.add(new CrimeHotspot(
            19.0596, 72.8295, "theft", "medium",
            now - (2 * oneDay), "Bandra Linking Road"
        ));
        
        // Borivali
        crimeDatabase.add(new CrimeHotspot(
            19.2304, 72.8571, "robbery", "low",
            now - (6 * oneDay), "Borivali National Park"
        ));
        
        // Colaba
        crimeDatabase.add(new CrimeHotspot(
            18.9067, 72.8147, "theft", "high",
            now - oneDay, "Colaba Causeway"
        ));
    }
    
    /**
     * Hyderabad Crime Hotspots
     */
    private static void loadHyderabadData() {
        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;
        
        // Hitech City
        crimeDatabase.add(new CrimeHotspot(
            17.4435, 78.3772, "theft", "medium",
            now - (2 * oneDay), "Hitech City"
        ));
        
        // Gachibowli
        crimeDatabase.add(new CrimeHotspot(
            17.4400, 78.3489, "harassment", "low",
            now - (4 * oneDay), "Gachibowli"
        ));
        
        // Secunderabad
        crimeDatabase.add(new CrimeHotspot(
            17.4399, 78.4983, "theft", "high",
            now - oneDay, "Secunderabad Station"
        ));
        
        // Kukatpally
        crimeDatabase.add(new CrimeHotspot(
            17.4849, 78.3915, "robbery", "medium",
            now - (3 * oneDay), "Kukatpally Housing Board"
        ));
    }
    
    /**
     * Calculate distance between two coordinates (in km)
     */
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}