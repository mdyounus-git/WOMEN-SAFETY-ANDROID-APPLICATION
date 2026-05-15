package com.example.womensafety;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Locale;

public class CrimeRiskActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST = 100;
    
    private FusedLocationProviderClient fusedLocationClient;
    private Button analyzeButton, refreshButton;
    private ProgressBar progressBar;
    private LinearLayout resultsContainer;
    private TextView locationNameText, riskLevelText, riskScoreText, safetyTipText;
    private LinearLayout factorsContainer, alertsContainer;
    
    private double currentLatitude;
    private double currentLongitude;
    private String currentLocationName;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        createUI();
        checkPermissions();
    }
    
    private void createUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F5F5F5"));
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);
        
        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Crime Risk Analysis");
        titleText.setTextSize(28);
        titleText.setTextColor(Color.parseColor("#333333"));
        titleText.setPadding(0, 0, 0, 16);
        mainLayout.addView(titleText);
        
        TextView subtitleText = new TextView(this);
        subtitleText.setText("Check safety level of your current location");
        subtitleText.setTextSize(14);
        subtitleText.setTextColor(Color.parseColor("#666666"));
        subtitleText.setPadding(0, 0, 0, 48);
        mainLayout.addView(subtitleText);
        
        // Analyze Button
        analyzeButton = new Button(this);
        analyzeButton.setText("Analyze Current Location");
        analyzeButton.setTextSize(18);
        LinearLayout.LayoutParams analyzeBtnParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 150);
        analyzeBtnParams.setMargins(0, 0, 0, 32);
        analyzeButton.setLayoutParams(analyzeBtnParams);
        analyzeButton.setOnClickListener(v -> getCurrentLocationAndAnalyze());
        mainLayout.addView(analyzeButton);
        
        // Progress Bar
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        progressParams.setMargins(0, 0, 0, 32);
        progressBar.setLayoutParams(progressParams);
        mainLayout.addView(progressBar);
        
        // Results Container
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setBackgroundColor(Color.WHITE);
        resultsContainer.setPadding(32, 32, 32, 32);
        resultsContainer.setVisibility(LinearLayout.GONE);
        
        locationNameText = new TextView(this);
        locationNameText.setTextSize(18);
        locationNameText.setPadding(0, 0, 0, 32);
        resultsContainer.addView(locationNameText);
        
        // Risk Level Card
        LinearLayout riskCard = new LinearLayout(this);
        riskCard.setOrientation(LinearLayout.VERTICAL);
        riskCard.setPadding(48, 48, 48, 48);
        riskCard.setBackgroundColor(Color.parseColor("#E8F5E9"));
        LinearLayout.LayoutParams riskCardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        riskCardParams.setMargins(0, 0, 0, 32);
        riskCard.setLayoutParams(riskCardParams);
        
        TextView riskLabelText = new TextView(this);
        riskLabelText.setText("RISK LEVEL");
        riskLabelText.setTextSize(14);
        riskLabelText.setTextColor(Color.parseColor("#666666"));
        riskLabelText.setGravity(Gravity.CENTER);
        riskLabelText.setPadding(0, 0, 0, 16);
        riskCard.addView(riskLabelText);
        
        riskLevelText = new TextView(this);
        riskLevelText.setTextSize(32);
        riskLevelText.setGravity(Gravity.CENTER);
        riskLevelText.setPadding(0, 0, 0, 16);
        riskCard.addView(riskLevelText);
        
        riskScoreText = new TextView(this);
        riskScoreText.setTextSize(20);
        riskScoreText.setGravity(Gravity.CENTER);
        riskCard.addView(riskScoreText);
        
        resultsContainer.addView(riskCard);
        
        // Risk Factors
        TextView factorsTitle = new TextView(this);
        factorsTitle.setText("Risk Factors:");
        factorsTitle.setTextSize(16);
        factorsTitle.setPadding(0, 0, 0, 24);
        resultsContainer.addView(factorsTitle);
        
        factorsContainer = new LinearLayout(this);
        factorsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams factorsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        factorsParams.setMargins(0, 0, 0, 48);
        factorsContainer.setLayoutParams(factorsParams);
        resultsContainer.addView(factorsContainer);
        
        // Nearby Alerts
        TextView alertsTitle = new TextView(this);
        alertsTitle.setText("Nearby Alerts:");
        alertsTitle.setTextSize(16);
        alertsTitle.setPadding(0, 0, 0, 24);
        resultsContainer.addView(alertsTitle);
        
        alertsContainer = new LinearLayout(this);
        alertsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams alertsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alertsParams.setMargins(0, 0, 0, 48);
        alertsContainer.setLayoutParams(alertsParams);
        resultsContainer.addView(alertsContainer);
        
        // Safety Tip
        TextView safetyTitle = new TextView(this);
        safetyTitle.setText("Safety Tip:");
        safetyTitle.setTextSize(16);
        safetyTitle.setPadding(0, 0, 0, 16);
        resultsContainer.addView(safetyTitle);
        
        safetyTipText = new TextView(this);
        safetyTipText.setTextSize(14);
        safetyTipText.setTextColor(Color.parseColor("#666666"));
        safetyTipText.setPadding(0, 0, 0, 32);
        resultsContainer.addView(safetyTipText);
        
        // Refresh Button
        refreshButton = new Button(this);
        refreshButton.setText("Refresh Analysis");
        refreshButton.setVisibility(Button.GONE);
        refreshButton.setOnClickListener(v -> getCurrentLocationAndAnalyze());
        resultsContainer.addView(refreshButton);
        
        mainLayout.addView(resultsContainer);
        
        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                PERMISSION_REQUEST);
        }
    }
    
    private void getCurrentLocationAndAnalyze() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        analyzeButton.setEnabled(false);
        resultsContainer.setVisibility(LinearLayout.GONE);
        
        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, location -> {
                if (location != null) {
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();
                    
                    getLocationName(location);
                    analyzeRisk();
                } else {
                    progressBar.setVisibility(ProgressBar.GONE);
                    analyzeButton.setEnabled(true);
                    Toast.makeText(this, "Unable to get location. Please try again.", 
                                  Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                progressBar.setVisibility(ProgressBar.GONE);
                analyzeButton.setEnabled(true);
                Toast.makeText(this, "Location error: " + e.getMessage(), 
                              Toast.LENGTH_SHORT).show();
            });
    }
    
    private void getLocationName(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                location.getLatitude(), 
                location.getLongitude(), 
                1
            );
            
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder locationName = new StringBuilder();
                
                if (address.getSubLocality() != null) {
                    locationName.append(address.getSubLocality());
                }
                if (address.getLocality() != null) {
                    if (locationName.length() > 0) locationName.append(", ");
                    locationName.append(address.getLocality());
                }
                
                currentLocationName = locationName.length() > 0 ? 
                    locationName.toString() : "Unknown Location";
            } else {
                currentLocationName = "Unknown Location";
            }
            
        } catch (Exception e) {
            currentLocationName = "Unknown Location";
        }
    }
    
    private void analyzeRisk() {
        new Thread(() -> {
            final CrimeRiskAnalyzer.RiskResult result = 
                CrimeRiskAnalyzer.analyzeLocation(
                    currentLatitude, 
                    currentLongitude, 
                    currentLocationName
                );
            
            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.GONE);
                analyzeButton.setEnabled(true);
                displayResults(result);
            });
        }).start();
    }
    
    private void displayResults(CrimeRiskAnalyzer.RiskResult result) {
        resultsContainer.setVisibility(LinearLayout.VISIBLE);
        refreshButton.setVisibility(Button.VISIBLE);
        
        locationNameText.setText("📍 " + result.locationName);
        
        String levelText = result.level.toString();
        int levelColor;
        
        switch (result.level) {
            case LOW:
                levelColor = Color.parseColor("#4CAF50");
                break;
            case MODERATE:
                levelColor = Color.parseColor("#FF9800");
                break;
            case HIGH:
            case CRITICAL:
                levelColor = Color.parseColor("#F44336");
                break;
            default:
                levelColor = Color.parseColor("#666666");
        }
        
        riskLevelText.setText(levelText);
        riskLevelText.setTextColor(levelColor);
        riskScoreText.setText(String.format("%.1f/10", result.score));
        riskScoreText.setTextColor(levelColor);
        safetyTipText.setText(result.safetyTip);
        
        factorsContainer.removeAllViews();
        for (CrimeRiskAnalyzer.RiskFactor factor : result.factors) {
            LinearLayout factorView = createFactorView(factor);
            factorsContainer.addView(factorView);
        }
        
        alertsContainer.removeAllViews();
        if (result.nearbyAlerts.isEmpty()) {
            TextView noAlertsText = new TextView(this);
            noAlertsText.setText("No recent alerts in your area");
            noAlertsText.setTextColor(Color.parseColor("#666666"));
            noAlertsText.setPadding(16, 16, 16, 16);
            alertsContainer.addView(noAlertsText);
        } else {
            for (CrimeRiskAnalyzer.NearbyAlert alert : result.nearbyAlerts) {
                LinearLayout alertView = createAlertView(alert);
                alertsContainer.addView(alertView);
            }
        }
    }
    
    private LinearLayout createFactorView(CrimeRiskAnalyzer.RiskFactor factor) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(16, 24, 16, 24);
        
        TextView iconText = new TextView(this);
        iconText.setText(factor.icon);
        iconText.setTextSize(24);
        iconText.setPadding(0, 0, 32, 0);
        layout.addView(iconText);
        
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        contentLayout.setLayoutParams(contentParams);
        
        TextView nameText = new TextView(this);
        nameText.setText(factor.name);
        nameText.setTextSize(16);
        int factorColor = factor.status.equals("safe") ? 
            Color.parseColor("#4CAF50") :
            factor.status.equals("warning") ?
            Color.parseColor("#FF9800") :
            Color.parseColor("#F44336");
        nameText.setTextColor(factorColor);
        contentLayout.addView(nameText);
        
        TextView descText = new TextView(this);
        descText.setText(factor.description);
        descText.setTextSize(12);
        descText.setTextColor(Color.parseColor("#666666"));
        contentLayout.addView(descText);
        
        layout.addView(contentLayout);
        
        return layout;
    }
    
    private LinearLayout createAlertView(CrimeRiskAnalyzer.NearbyAlert alert) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(16, 24, 16, 24);
        layout.setBackgroundColor(Color.parseColor("#EEEEEE"));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, 8);
        layout.setLayoutParams(layoutParams);
        
        TextView severityText = new TextView(this);
        severityText.setText(
            alert.severity.equals("high") ? "🔴" :
            alert.severity.equals("medium") ? "🟡" : "🟢"
        );
        severityText.setTextSize(20);
        severityText.setPadding(0, 0, 24, 0);
        layout.addView(severityText);
        
        LinearLayout detailsLayout = new LinearLayout(this);
        detailsLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        detailsLayout.setLayoutParams(detailsParams);
        
        TextView typeText = new TextView(this);
        typeText.setText(alert.type);
        typeText.setTextSize(14);
        typeText.setTextColor(Color.parseColor("#333333"));
        detailsLayout.addView(typeText);
        
        TextView infoText = new TextView(this);
        infoText.setText(alert.distance + " • " + alert.timeAgo);
        infoText.setTextSize(12);
        infoText.setTextColor(Color.parseColor("#666666"));
        detailsLayout.addView(infoText);
        
        layout.addView(detailsLayout);
        
        return layout;
    }
}