package com.example.womensafety;

import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Crime Risk Analysis Engine
 * Calculates safety score based on location, time, and crime data
 */
public class CrimeRiskAnalyzer {
    
    private static final String TAG = "CrimeRiskAnalyzer";
    
    public enum RiskLevel {
        LOW, MODERATE, HIGH, CRITICAL
    }
    
    public static class RiskResult {
        public RiskLevel level;
        public double score; // 0-10 (0=safest, 10=most dangerous)
        public List<RiskFactor> factors;
        public List<NearbyAlert> nearbyAlerts;
        public String locationName;
        public String safetyTip;
        
        public RiskResult() {
            factors = new ArrayList<>();
            nearbyAlerts = new ArrayList<>();
        }
    }
    
    public static class RiskFactor {
        public String name;
        public String status; // "safe", "warning", "danger"
        public String icon;
        public String description;
        
        public RiskFactor(String name, String status, String icon, String description) {
            this.name = name;
            this.status = status;
            this.icon = icon;
            this.description = description;
        }
    }
    
    public static class NearbyAlert {
        public String type; // "theft", "assault", "harassment", etc.
        public String distance; // "0.5 km", "1.2 km"
        public String timeAgo; // "2 hours ago", "1 day ago"
        public String severity; // "low", "medium", "high"
        
        public NearbyAlert(String type, String distance, String timeAgo, String severity) {
            this.type = type;
            this.distance = distance;
            this.timeAgo = timeAgo;
            this.severity = severity;
        }
    }
    
    /**
     * Main risk analysis method
     */
    public static RiskResult analyzeLocation(double latitude, double longitude, String locationName) {
        Log.d(TAG, "🔍 Analyzing risk for: " + locationName);
        RiskResult result = new RiskResult();
        result.locationName = locationName;
        
        double baseScore = 0.0;
        
        // Factor 1: Time of day (0-3 points)
        double timeScore = analyzeTimeOfDay(result);
        baseScore += timeScore;
        
        // Factor 2: Area type (0-3 points)
        double areaScore = analyzeAreaType(latitude, longitude, locationName, result);
        baseScore += areaScore;
        
        // Factor 3: Historical crime data (0-4 points)
        double crimeScore = analyzeCrimeHistory(latitude, longitude, result);
        baseScore += crimeScore;
        
        // Calculate final score (0-10)
        result.score = Math.min(10.0, baseScore);
        
        // Determine risk level
        if (result.score < 3.0) {
            result.level = RiskLevel.LOW;
            result.safetyTip = "Area appears safe. Stay alert and avoid isolated spots.";
        } else if (result.score < 6.0) {
            result.level = RiskLevel.MODERATE;
            result.safetyTip = "Moderate risk. Stay on main roads and avoid traveling alone at night.";
        } else if (result.score < 8.0) {
            result.level = RiskLevel.HIGH;
            result.safetyTip = "High risk area. Use well-lit routes and consider alternative transportation.";
        } else {
            result.level = RiskLevel.CRITICAL;
            result.safetyTip = "Critical risk. Avoid this area if possible. Stay in groups and remain alert.";
        }
        
        // Add nearby crime alerts
        addNearbyAlerts(latitude, longitude, result);
        
        Log.d(TAG, "✅ Risk Score: " + result.score + "/10, Level: " + result.level);
        return result;
    }
    
    /**
     * Analyze time of day risk
     */
    private static double analyzeTimeOfDay(RiskResult result) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        
        double score;
        String status;
        String description;
        
        if (hour >= 6 && hour < 10) {
            // Early morning (6 AM - 10 AM)
            score = 0.5;
            status = "safe";
            description = "Early morning - Generally safe with good visibility";
        } else if (hour >= 10 && hour < 18) {
            // Daytime (10 AM - 6 PM)
            score = 0.3;
            status = "safe";
            description = "Daytime - High foot traffic and visibility";
        } else if (hour >= 18 && hour < 22) {
            // Evening (6 PM - 10 PM)
            score = 1.5;
            status = "warning";
            description = "Evening - Moderate risk, reduced visibility";
        } else if (hour >= 22 || hour < 2) {
            // Late night (10 PM - 2 AM)
            score = 2.5;
            status = "danger";
            description = "Late night - Higher risk, low foot traffic";
        } else {
            // Very late night (2 AM - 6 AM)
            score = 3.0;
            status = "danger";
            description = "Very late night - Highest risk, minimal activity";
        }
        
        result.factors.add(new RiskFactor(
            "Time of Day",
            status,
            "🕐",
            description
        ));
        
        return score;
    }
    
    /**
     * Analyze area type characteristics
     */
    private static double analyzeAreaType(double lat, double lon, String locationName, RiskResult result) {
        // Simplified area classification based on location name
        String lowerName = locationName.toLowerCase();
        
        double score;
        String lightingStatus;
        String crowdStatus;
        
        // Check for commercial/residential indicators
        if (lowerName.contains("mall") || lowerName.contains("market") || 
            lowerName.contains("plaza") || lowerName.contains("center")) {
            // Commercial area
            score = 0.5;
            lightingStatus = "safe";
            crowdStatus = "safe";
            
            result.factors.add(new RiskFactor(
                "Well-lit Area",
                "safe",
                "💡",
                "Commercial zone with good lighting"
            ));
            result.factors.add(new RiskFactor(
                "High Foot Traffic",
                "safe",
                "👥",
                "Busy area with many people"
            ));
            
        } else if (lowerName.contains("park") || lowerName.contains("garden")) {
            // Park/Garden
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            
            if (hour >= 18 || hour < 6) {
                score = 2.0;
                lightingStatus = "warning";
                crowdStatus = "warning";
                
                result.factors.add(new RiskFactor(
                    "Isolated Area",
                    "warning",
                    "🌳",
                    "Park area - Low activity at night"
                ));
            } else {
                score = 0.8;
                lightingStatus = "safe";
                crowdStatus = "safe";
                
                result.factors.add(new RiskFactor(
                    "Open Space",
                    "safe",
                    "🌳",
                    "Park area - Good visibility during day"
                ));
            }
            
        } else if (lowerName.contains("station") || lowerName.contains("bus") || 
                   lowerName.contains("metro") || lowerName.contains("railway")) {
            // Transport hub
            score = 1.2;
            lightingStatus = "safe";
            crowdStatus = "warning";
            
            result.factors.add(new RiskFactor(
                "Transport Hub",
                "warning",
                "🚇",
                "High crowd but potential pickpocketing risk"
            ));
            
        } else if (lowerName.contains("highway") || lowerName.contains("road")) {
            // Highway/Main road
            score = 1.5;
            lightingStatus = "warning";
            crowdStatus = "warning";
            
            result.factors.add(new RiskFactor(
                "Main Road",
                "warning",
                "🛣️",
                "Vehicle traffic but limited pedestrian activity"
            ));
            
        } else {
            // Residential/Unknown area
            score = 1.0;
            lightingStatus = "safe";
            crowdStatus = "safe";
            
            result.factors.add(new RiskFactor(
                "Residential Area",
                "safe",
                "🏘️",
                "Moderate activity and lighting"
            ));
        }
        
        return score;
    }
    
    /**
     * Analyze historical crime data
     * This uses a pre-loaded database of high-risk areas
     */
    private static double analyzeCrimeHistory(double lat, double lon, RiskResult result) {
        // Get crime hotspots from database
        List<CrimeDataManager.CrimeHotspot> nearbyHotspots = 
            CrimeDataManager.getNearbyHotspots(lat, lon, 2.0); // 2km radius
        
        if (nearbyHotspots.isEmpty()) {
            result.factors.add(new RiskFactor(
                "Crime History",
                "safe",
                "📊",
                "No recent incidents reported in this area"
            ));
            return 0.5;
        }
        
        // Calculate score based on nearby crime incidents
        double crimeScore = 0.0;
        int highSeverityCrimes = 0;
        
        for (CrimeDataManager.CrimeHotspot hotspot : nearbyHotspots) {
            double distance = calculateDistance(lat, lon, hotspot.latitude, hotspot.longitude);
            
            // Score decreases with distance
            double distanceFactor = Math.max(0, 1.0 - (distance / 2.0)); // Max effect within 2km
            
            // Score increases with severity
            double severityFactor = 0.5;
            if (hotspot.severity.equals("high")) {
                severityFactor = 1.5;
                highSeverityCrimes++;
            } else if (hotspot.severity.equals("medium")) {
                severityFactor = 1.0;
            }
            
            crimeScore += distanceFactor * severityFactor;
        }
        
        // Add crime history factor
        String status = crimeScore < 1.0 ? "safe" : crimeScore < 2.5 ? "warning" : "danger";
        result.factors.add(new RiskFactor(
            "Crime History",
            status,
            "📊",
            nearbyHotspots.size() + " incident(s) reported within 2km"
        ));
        
        return Math.min(4.0, crimeScore);
    }
    
    /**
     * Add nearby crime alerts
     */
    private static void addNearbyAlerts(double lat, double lon, RiskResult result) {
        List<CrimeDataManager.CrimeHotspot> hotspots = 
            CrimeDataManager.getNearbyHotspots(lat, lon, 3.0); // 3km radius
        
        for (CrimeDataManager.CrimeHotspot hotspot : hotspots) {
            double distance = calculateDistance(lat, lon, hotspot.latitude, hotspot.longitude);
            
            result.nearbyAlerts.add(new NearbyAlert(
                formatCrimeType(hotspot.type),
                String.format("%.1f km", distance),
                formatTimeAgo(hotspot.timestamp),
                hotspot.severity
            ));
        }
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
    
    /**
     * Format crime type for display
     */
    private static String formatCrimeType(String type) {
        switch (type) {
            case "theft": return "Theft reported";
            case "assault": return "Assault incident";
            case "harassment": return "Harassment reported";
            case "robbery": return "Robbery incident";
            case "vandalism": return "Vandalism reported";
            default: return "Incident reported";
        }
    }
    
    /**
     * Format timestamp as "X time ago"
     */
    private static String formatTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "Just now";
        }
    }
}