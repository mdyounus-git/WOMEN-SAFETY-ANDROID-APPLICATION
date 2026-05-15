package com.example.womensafety;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ProfileActivity extends AppCompatActivity {
    private ImageView imgProfilePicture;
    private TextView tvProfileName, tvProfileEmail;
    private SharedPrefManager prefManager;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private File profileImageFile;
    private LinearLayout bottomNavHome, bottomNavGallery, bottomNavAI, bottomNavProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefManager = SharedPrefManager.getInstance(this);
        profileImageFile = new File(getFilesDir(), "profile_picture.jpg");
        setupImagePicker();
        createPremiumUI();
        loadUserData();
        loadProfilePicture();
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    saveProfilePicture(imageUri);
                }
            }
        });
    }

    private void createPremiumUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        createHeader(mainLayout);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollView.setLayoutParams(scrollParams);
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(24, 24, 24, 120);
        createProfileCard(contentLayout);
        createAccountInfoCard(contentLayout);
        createActionsCard(contentLayout);
        scrollView.addView(contentLayout);
        mainLayout.addView(scrollView);
        createBottomNavigation(mainLayout);
        setContentView(mainLayout);
    }

    private void createHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(24, 48, 24, 24);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7")});
        header.setBackground(gradient);
        TextView backBtn = new TextView(this);
        backBtn.setText("←");
        backBtn.setTextSize(28);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setPadding(8, 8, 16, 8);
        backBtn.setOnClickListener(v -> finish());
        header.addView(backBtn);
        TextView title = new TextView(this);
        title.setText("Profile");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.weight = 1;
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        title.setLayoutParams(titleParams);
        header.addView(title);
        parent.addView(header);
    }

    private void createProfileCard(LinearLayout parent) {
        CardView card = createPremiumCard();
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER);
        cardContent.setPadding(32, 40, 32, 40);
        LinearLayout avatarContainer = new LinearLayout(this);
        LinearLayout.LayoutParams avatarContainerParams = new LinearLayout.LayoutParams(dpToPx(120), dpToPx(120));
        avatarContainer.setLayoutParams(avatarContainerParams);
        avatarContainer.setGravity(Gravity.CENTER);
        GradientDrawable outerRing = new GradientDrawable();
        outerRing.setShape(GradientDrawable.OVAL);
        outerRing.setStroke(dpToPx(4), Color.parseColor("#8B5CF6"));
        avatarContainer.setBackground(outerRing);
        LinearLayout innerCircle = new LinearLayout(this);
        LinearLayout.LayoutParams innerParams = new LinearLayout.LayoutParams(dpToPx(108), dpToPx(108));
        innerCircle.setLayoutParams(innerParams);
        innerCircle.setGravity(Gravity.CENTER);
        GradientDrawable innerBg = new GradientDrawable();
        innerBg.setShape(GradientDrawable.OVAL);
        innerBg.setColor(Color.parseColor("#E9D5FF"));
        innerCircle.setBackground(innerBg);
        innerCircle.setClipToOutline(true);
        imgProfilePicture = new ImageView(this);
        imgProfilePicture.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imgProfilePicture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgProfilePicture.setOnClickListener(v -> openImagePicker());
        innerCircle.addView(imgProfilePicture);
        avatarContainer.addView(innerCircle);
        cardContent.addView(avatarContainer);
        TextView editIcon = new TextView(this);
        editIcon.setText("📷");
        editIcon.setTextSize(20);
        editIcon.setPadding(12, 12, 12, 12);
        GradientDrawable editBg = new GradientDrawable();
        editBg.setShape(GradientDrawable.OVAL);
        editBg.setColor(Color.WHITE);
        editIcon.setBackground(editBg);
        editIcon.setElevation(4);
        editIcon.setOnClickListener(v -> openImagePicker());
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = -dpToPx(30);
        editIcon.setLayoutParams(editParams);
        cardContent.addView(editIcon);
        tvProfileName = new TextView(this);
        tvProfileName.setTextSize(24);
        tvProfileName.setTextColor(Color.parseColor("#1F2937"));
        tvProfileName.setTypeface(null, Typeface.BOLD);
        tvProfileName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dpToPx(16);
        tvProfileName.setLayoutParams(nameParams);
        cardContent.addView(tvProfileName);
        tvProfileEmail = new TextView(this);
        tvProfileEmail.setTextSize(14);
        tvProfileEmail.setTextColor(Color.parseColor("#6B7280"));
        tvProfileEmail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emailParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emailParams.topMargin = dpToPx(4);
        tvProfileEmail.setLayoutParams(emailParams);
        cardContent.addView(tvProfileEmail);
        card.addView(cardContent);
        parent.addView(card);
    }

    private void createAccountInfoCard(LinearLayout parent) {
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dpToPx(24);
        CardView card = createPremiumCard();
        card.setLayoutParams(cardParams);
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(32, 32, 32, 32);
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("Account Information");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(Color.parseColor("#1F2937"));
        sectionTitle.setTypeface(null, Typeface.BOLD);
        cardContent.addView(sectionTitle);
        addInfoRow(cardContent, "👤", "Name", prefManager.getUserName());
        addInfoRow(cardContent, "📧", "Email", prefManager.getUserEmail());
        addInfoRow(cardContent, "📱", "Phone", prefManager.getUserPhone() != null ? prefManager.getUserPhone() : "Not set");
        card.addView(cardContent);
        parent.addView(card);
    }

    private void addInfoRow(LinearLayout parent, String icon, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dpToPx(20);
        row.setLayoutParams(rowParams);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 16, 16, 16);
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setShape(GradientDrawable.RECTANGLE);
        rowBg.setColor(Color.parseColor("#F9FAFB"));
        rowBg.setCornerRadius(12);
        row.setBackground(rowBg);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setPadding(0, 0, 16, 0);
        row.addView(iconView);
        LinearLayout textLayout = new LinearLayout(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.weight = 1;
        textLayout.setLayoutParams(textParams);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(Color.parseColor("#6B7280"));
        textLayout.addView(labelView);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(16);
        valueView.setTextColor(Color.parseColor("#1F2937"));
        valueView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dpToPx(4);
        valueView.setLayoutParams(valueParams);
        textLayout.addView(valueView);
        row.addView(textLayout);
        parent.addView(row);
    }

    private void createActionsCard(LinearLayout parent) {
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dpToPx(24);
        CardView card = createPremiumCard();
        card.setLayoutParams(cardParams);
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(32, 32, 32, 32);
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("Actions");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(Color.parseColor("#1F2937"));
        sectionTitle.setTypeface(null, Typeface.BOLD);
        cardContent.addView(sectionTitle);
        addActionButton(cardContent, "🗑️", "Clear Gallery Data", "Delete all photos and audio", this::showClearGalleryDialog);
        addActionButton(cardContent, "ℹ️", "About App", "Version and features", this::showAboutDialog);
        addActionButton(cardContent, "🚪", "Logout", "Sign out of your account", this::showLogoutDialog);
        card.addView(cardContent);
        parent.addView(card);
    }

    private void addActionButton(LinearLayout parent, String icon, String title, String subtitle, Runnable action) {
        LinearLayout btn = new LinearLayout(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dpToPx(16);
        btn.setLayoutParams(btnParams);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setPadding(16, 16, 16, 16);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setColor(Color.parseColor("#F9FAFB"));
        btnBg.setCornerRadius(12);
        btn.setBackground(btnBg);
        btn.setOnClickListener(v -> action.run());
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setPadding(0, 0, 16, 0);
        btn.addView(iconView);
        LinearLayout textLayout = new LinearLayout(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.weight = 1;
        textLayout.setLayoutParams(textParams);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Color.parseColor("#1F2937"));
        titleView.setTypeface(null, Typeface.BOLD);
        textLayout.addView(titleView);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(Color.parseColor("#6B7280"));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dpToPx(4);
        subtitleView.setLayoutParams(subParams);
        textLayout.addView(subtitleView);
        btn.addView(textLayout);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(Color.parseColor("#9CA3AF"));
        btn.addView(arrow);
        parent.addView(btn);
    }

    private void createBottomNavigation(LinearLayout parent) {
        LinearLayout bottomNavContainer = new LinearLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomNavContainer.setLayoutParams(containerParams);
        bottomNavContainer.setOrientation(LinearLayout.VERTICAL);
        bottomNavContainer.setPadding(20, 12, 20, 32);
        bottomNavContainer.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout bottomNav = new LinearLayout(this);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomNav.setLayoutParams(navParams);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(12, 20, 12, 20);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setShape(GradientDrawable.RECTANGLE);
        navBg.setColor(Color.WHITE);
        navBg.setCornerRadius(32);
        bottomNav.setBackground(navBg);
        bottomNav.setElevation(16);
        bottomNavHome = createNavItem("🏠", "Home", false);
        bottomNavHome.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        bottomNav.addView(bottomNavHome);
        bottomNavGallery = createNavItem("📸", "Gallery", false);
        bottomNavGallery.setOnClickListener(v -> {
            startActivity(new Intent(this, GalleryActivity.class));
            finish();
        });
        bottomNav.addView(bottomNavGallery);
        bottomNavAI = createNavItem("🤖", "AI Tools", false);
        bottomNav.addView(bottomNavAI);
        bottomNavProfile = createNavItem("👤", "Profile", true);
        bottomNav.addView(bottomNavProfile);
        bottomNavContainer.addView(bottomNav);
        parent.addView(bottomNavContainer);
    }

    private LinearLayout createNavItem(String icon, String label, boolean isActive) {
        LinearLayout item = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.weight = 1;
        item.setLayoutParams(params);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(12, 14, 12, 14);
        if (isActive) {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setShape(GradientDrawable.RECTANGLE);
            activeBg.setColor(Color.parseColor("#8B5CF6"));
            activeBg.setAlpha(30);
            activeBg.setCornerRadius(20);
            item.setBackground(activeBg);
        }
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(26);
        iconView.setGravity(Gravity.CENTER);
        item.addView(iconView);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(isActive ? Color.parseColor("#8B5CF6") : Color.parseColor("#6B7280"));
        labelView.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = 6;
        labelView.setLayoutParams(labelParams);
        item.addView(labelView);
        return item;
    }

    private CardView createPremiumCard() {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setRadius(24);
        card.setCardElevation(8);
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    private void loadUserData() {
        String name = prefManager.getUserName();
        String email = prefManager.getUserEmail();
        tvProfileName.setText(name);
        tvProfileEmail.setText(email);
    }

    private void loadProfilePicture() {
        if (profileImageFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(profileImageFile.getAbsolutePath());
            if (bitmap != null) {
                imgProfilePicture.setImageBitmap(bitmap);
            }
        } else {
            TextView placeholder = new TextView(this);
            placeholder.setText(prefManager.getUserName().substring(0, 1).toUpperCase());
            placeholder.setTextSize(48);
            placeholder.setTextColor(Color.parseColor("#8B5CF6"));
            placeholder.setGravity(Gravity.CENTER);
        }
    }

    private void saveProfilePicture(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                int maxSize = 512;
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
                int newWidth = Math.round(ratio * width);
                int newHeight = Math.round(ratio * height);
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                FileOutputStream fos = new FileOutputStream(profileImageFile);
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.flush();
                fos.close();
                imgProfilePicture.setImageBitmap(resizedBitmap);
                bitmap.recycle();
                resizedBitmap.recycle();
                Toast.makeText(this, "✅ Profile picture updated!", Toast.LENGTH_SHORT).show();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save profile picture", Toast.LENGTH_SHORT).show();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showClearGalleryDialog() {
        new AlertDialog.Builder(this).setTitle("Clear Gallery Data").setMessage("This will delete all captured photos and audio recordings. This action cannot be undone.\n\nAre you sure?").setPositiveButton("Clear All", (dialog, which) -> clearAllGalleryData()).setNegativeButton("Cancel", null).show();
    }

    private void clearAllGalleryData() {
        boolean photosDeleted = deleteDirectory(new File(getExternalFilesDir(null), "SOSPhotos"));
        boolean audioDeleted = deleteDirectory(new File(getExternalFilesDir(null), "SOSAudio"));
        if (photosDeleted || audioDeleted) {
            Toast.makeText(this, "✅ Gallery data cleared successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No data to clear", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }
        return false;
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this).setTitle("About Women Safety App").setMessage("Version: 1.0.0\n\nWomen Safety App is designed to provide emergency assistance in critical situations.\n\nFeatures:\n• Quick SOS activation\n• Volume button trigger (Hold Up 6 sec)\n• SMS alerts to emergency contacts\n• Real-time location sharing\n• Automatic photo capture\n• Audio recording\n• In-app gallery\n• Profile customization\n\nStay safe!").setPositiveButton("OK", null).show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this).setTitle("Logout").setMessage("Are you sure you want to logout?").setPositiveButton("Yes", (dialog, which) -> {
            prefManager.logout();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("No", null).show();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}