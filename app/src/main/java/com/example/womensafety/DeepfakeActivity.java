package com.example.womensafety;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.InputStream;

/**
 * Deepfake Detection Activity - CNN-based image verification
 * Location: app/src/main/java/com/example/womensafety/DeepfakeActivity.java
 */
public class DeepfakeActivity extends AppCompatActivity {
    
    private static final int PICK_IMAGE = 1;
    private static final int TAKE_PHOTO = 2;
    private static final int PERMISSION_REQUEST = 100;
    
    private ImageView imageView;
    private Button selectButton, cameraButton, analyzeButton;
    private ProgressBar progressBar;
    private LinearLayout resultsContainer;
    private TextView resultTitle, confidenceText, summaryText;
    private LinearLayout indicatorsContainer;
    private Bitmap selectedImage;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create UI programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F5F5F5"));
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        // Title
        TextView title = new TextView(this);
        title.setText("🔍 Deepfake Detection");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#1A1A1A"));
        title.setPadding(0, 0, 0, 20);
        mainLayout.addView(title);
        
        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Upload an image to verify its authenticity using CNN-based AI detection");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#666666"));
        subtitle.setPadding(0, 0, 0, 40);
        mainLayout.addView(subtitle);
        
        // Image preview
        imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 600
        ));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams imgParams = (LinearLayout.LayoutParams) imageView.getLayoutParams();
        imgParams.setMargins(0, 0, 0, 30);
        imageView.setLayoutParams(imgParams);
        mainLayout.addView(imageView);
        
        // Buttons container
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        // Select image button
        selectButton = new Button(this);
        selectButton.setText("📁 Select Image");
        selectButton.setTextSize(16);
        selectButton.setTextColor(Color.WHITE);
        selectButton.setBackgroundColor(Color.parseColor("#8E24AA"));
        LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(
            0, 140, 1
        );
        btnParams1.setMargins(0, 0, 15, 30);
        selectButton.setLayoutParams(btnParams1);
        selectButton.setOnClickListener(v -> selectImage());
        buttonsLayout.addView(selectButton);
        
        // Camera button
        cameraButton = new Button(this);
        cameraButton.setText("📷 Take Photo");
        cameraButton.setTextSize(16);
        cameraButton.setTextColor(Color.WHITE);
        cameraButton.setBackgroundColor(Color.parseColor("#8E24AA"));
        LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
            0, 140, 1
        );
        btnParams2.setMargins(15, 0, 0, 30);
        cameraButton.setLayoutParams(btnParams2);
        cameraButton.setOnClickListener(v -> takePhoto());
        buttonsLayout.addView(cameraButton);
        
        mainLayout.addView(buttonsLayout);
        
        // Analyze button
        analyzeButton = new Button(this);
        analyzeButton.setText("🔍 Analyze Image");
        analyzeButton.setTextSize(18);
        analyzeButton.setTypeface(null, Typeface.BOLD);
        analyzeButton.setTextColor(Color.WHITE);
        analyzeButton.setBackgroundColor(Color.parseColor("#6A1B9A"));
        analyzeButton.setEnabled(false);
        LinearLayout.LayoutParams analyzeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 160
        );
        analyzeParams.setMargins(0, 0, 0, 30);
        analyzeButton.setLayoutParams(analyzeParams);
        analyzeButton.setOnClickListener(v -> analyzeImage());
        mainLayout.addView(analyzeButton);
        
        // Progress bar
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressParams.gravity = Gravity.CENTER;
        progressParams.setMargins(0, 0, 0, 30);
        progressBar.setLayoutParams(progressParams);
        mainLayout.addView(progressBar);
        
        // Results container
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setBackgroundColor(Color.WHITE);
        resultsContainer.setPadding(40, 40, 40, 40);
        resultsContainer.setVisibility(LinearLayout.GONE);
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resultsParams.setMargins(0, 20, 0, 0);
        resultsContainer.setLayoutParams(resultsParams);
        
        // Result title
        resultTitle = new TextView(this);
        resultTitle.setTextSize(32);
        resultTitle.setTypeface(null, Typeface.BOLD);
        resultTitle.setGravity(Gravity.CENTER);
        resultTitle.setPadding(0, 0, 0, 20);
        resultsContainer.addView(resultTitle);
        
        // Confidence text
        confidenceText = new TextView(this);
        confidenceText.setTextSize(20);
        confidenceText.setGravity(Gravity.CENTER);
        confidenceText.setPadding(0, 0, 0, 30);
        confidenceText.setTextColor(Color.parseColor("#333333"));
        resultsContainer.addView(confidenceText);
        
        // Summary text
        summaryText = new TextView(this);
        summaryText.setTextSize(14);
        summaryText.setPadding(0, 0, 0, 40);
        summaryText.setTextColor(Color.parseColor("#444444"));
        resultsContainer.addView(summaryText);
        
        // Indicators title
        TextView indicatorsTitle = new TextView(this);
        indicatorsTitle.setText("Detection Indicators:");
        indicatorsTitle.setTextSize(18);
        indicatorsTitle.setTypeface(null, Typeface.BOLD);
        indicatorsTitle.setTextColor(Color.parseColor("#1A1A1A"));
        indicatorsTitle.setPadding(0, 0, 0, 20);
        resultsContainer.addView(indicatorsTitle);
        
        // Indicators container
        indicatorsContainer = new LinearLayout(this);
        indicatorsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.addView(indicatorsContainer);
        
        mainLayout.addView(resultsContainer);
        scrollView.addView(mainLayout);
        setContentView(scrollView);
        
        // Check permissions
        checkPermissions();
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST);
        }
    }
    
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }
    
    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, TAKE_PHOTO);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            try {
                if (requestCode == PICK_IMAGE) {
                    Uri uri = data.getData();
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    selectedImage = BitmapFactory.decodeStream(inputStream);
                } else if (requestCode == TAKE_PHOTO) {
                    selectedImage = (Bitmap) data.getExtras().get("data");
                }
                
                if (selectedImage != null) {
                    imageView.setImageBitmap(selectedImage);
                    analyzeButton.setEnabled(true);
                    resultsContainer.setVisibility(LinearLayout.GONE);
                    Toast.makeText(this, "✅ Image loaded successfully!", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "❌ Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void analyzeImage() {
        if (selectedImage == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading
        progressBar.setVisibility(ProgressBar.VISIBLE);
        analyzeButton.setEnabled(false);
        resultsContainer.setVisibility(LinearLayout.GONE);
        
        // Perform analysis in background
        new Thread(() -> {
            DeepfakeDetector.DetectionResult result = DeepfakeDetector.analyzeImage(selectedImage);
            
            // Update UI on main thread
            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.GONE);
                analyzeButton.setEnabled(true);
                displayResults(result);
            });
        }).start();
    }
    
    private void displayResults(DeepfakeDetector.DetectionResult result) {
        // Set title and color
        if (result.isAuthentic) {
            resultTitle.setText("✅ AUTHENTIC");
            resultTitle.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            resultTitle.setText("⚠️ SUSPICIOUS");
            resultTitle.setTextColor(Color.parseColor("#D32F2F"));
        }
        
        // Set confidence
        confidenceText.setText(String.format("%.1f%% Confidence", result.confidenceScore));
        
        // Set summary
        summaryText.setText(result.summary);
        
        // Clear and add indicators
        indicatorsContainer.removeAllViews();
        for (String indicator : result.indicators) {
            TextView tv = new TextView(this);
            tv.setText(indicator);
            tv.setTextSize(14);
            tv.setTextColor(Color.parseColor("#1A1A1A"));
            tv.setPadding(20, 15, 20, 15);
            tv.setBackgroundColor(Color.parseColor("#F5F5F5"));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 10);
            tv.setLayoutParams(params);
            indicatorsContainer.addView(tv);
        }
        
        // Show results
        resultsContainer.setVisibility(LinearLayout.VISIBLE);
        
        Toast.makeText(this, "✅ Analysis complete!", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Permissions required for camera and storage", Toast.LENGTH_LONG).show();
            }
        }
    }
}