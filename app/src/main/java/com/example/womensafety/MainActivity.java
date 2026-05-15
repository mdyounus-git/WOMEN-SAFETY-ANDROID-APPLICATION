package com.example.womensafety;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private SharedPreferences sharedPreferences;
    private LinearLayout contactsContainer;
    private Button sosButton;
    private boolean isSosActive = false;
    private VolumeHoldDetector volumeDetector;
    private LinearLayout bottomNavHome, bottomNavGallery, bottomNavAI, bottomNavProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE);
        volumeDetector = new VolumeHoldDetector();
        volumeDetector.setContext(this);
        checkPermissions();
        createProfessionalUI();
        checkAccessibilityService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSOSButtonState();
    }

    private void checkAccessibilityService() {
        String service = "com.example.womensafety/com.example.womensafety.VolumeButtonAccessibilityService";
        if (!isAccessibilityServiceEnabled(service)) {
            new AlertDialog.Builder(this).setTitle("🔊 Enable Volume Button SOS").setMessage("To trigger SOS when screen is OFF/LOCKED:\n\n1. Tap 'Open Settings' below\n2. Find 'Women Safety' in the list\n3. Turn it ON\n4. Confirm 'Allow'\n\nThen hold Volume Up for 6 seconds anywhere!").setPositiveButton("Open Settings", (dialog, which) -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    Toast.makeText(this, "Find 'Women Safety' and turn it ON", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Please enable manually in Settings → Accessibility", Toast.LENGTH_LONG).show();
                }
            }).setNegativeButton("Later", null).setCancelable(true).show();
        }
    }

    private boolean isAccessibilityServiceEnabled(String service) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = android.provider.Settings.Secure.getInt(getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (android.provider.Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        if (accessibilityEnabled == 1) {
            String settingValue = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    private void updateSOSButtonState() {
        boolean isServiceRunning = isServiceRunning(SOSService.class);
        if (isServiceRunning && !isSosActive) {
            isSosActive = true;
            sosButton.setText("STOP SOS");
            GradientDrawable stopBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#059669"), Color.parseColor("#10B981")});
            stopBg.setCornerRadius(16);
            sosButton.setBackground(stopBg);
        } else if (!isServiceRunning && isSosActive) {
            isSosActive = false;
            sosButton.setText("ACTIVATE SOS");
            GradientDrawable activateBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#DC2626"), Color.parseColor("#EF4444")});
            activateBg.setCornerRadius(16);
            sosButton.setBackground(activateBg);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void createProfessionalUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        createHeader(mainLayout);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollView.setLayoutParams(scrollParams);
        scrollView.setFillViewport(true);
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(32, 24, 32, 120);
        createSOSCard(contentLayout);
        createContactsCard(contentLayout);
        createFeatureCards(contentLayout);
        scrollView.addView(contentLayout);
        mainLayout.addView(scrollView);
        createBottomNavigation(mainLayout);
        setContentView(mainLayout);
        loadContacts();
    }

    private void createHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7"), Color.parseColor("#EC4899")});
        header.setBackground(gradient);
        header.setPadding(32, 56, 32, 32);
        header.setElevation(8);
        TextView title = new TextView(this);
        title.setText("Women Safety");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.02f);
        header.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Your Safety, Our Priority");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.parseColor("#F3E8FF"));
        subtitle.setAlpha(0.95f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = 8;
        subtitle.setLayoutParams(subtitleParams);
        header.addView(subtitle);
        parent.addView(header);
    }

    private void createSOSCard(LinearLayout parent) {
        CardView card = createCard();
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(32, 32, 32, 32);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = new TextView(this);
        icon.setText("🛡️");
        icon.setTextSize(28);
        titleRow.addView(icon);
        TextView title = new TextView(this);
        title.setText("  Emergency SOS");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#1F2937"));
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title);
        cardContent.addView(titleRow);
        TextView desc = new TextView(this);
        desc.setText("Instantly sends emergency alerts with live location, photos, and audio recording to all your trusted contacts");
        desc.setTextSize(14);
        desc.setTextColor(Color.parseColor("#6B7280"));
        desc.setLineSpacing(4, 1);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = 16;
        desc.setLayoutParams(descParams);
        cardContent.addView(desc);
        sosButton = new Button(this);
        sosButton.setText("ACTIVATE SOS");
        sosButton.setTextSize(18);
        sosButton.setTextColor(Color.WHITE);
        sosButton.setTypeface(null, Typeface.BOLD);
        sosButton.setAllCaps(true);
        sosButton.setPadding(0, 48, 0, 48);
        GradientDrawable buttonBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#DC2626"), Color.parseColor("#EF4444")});
        buttonBg.setCornerRadius(16);
        sosButton.setBackground(buttonBg);
        sosButton.setElevation(4);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = 24;
        sosButton.setLayoutParams(btnParams);
        sosButton.setOnClickListener(v -> toggleSOS());
        cardContent.addView(sosButton);
        card.addView(cardContent);
        parent.addView(card);
    }

    private void createContactsCard(LinearLayout parent) {
        CardView card = createCard();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = 24;
        card.setLayoutParams(cardParams);
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(32, 32, 32, 32);
        LinearLayout header = new LinearLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView headerIcon = new TextView(this);
        headerIcon.setText("📞");
        headerIcon.setTextSize(24);
        header.addView(headerIcon);
        TextView titleView = new TextView(this);
        titleView.setText("  Emergency Contacts");
        titleView.setTextSize(20);
        titleView.setTextColor(Color.parseColor("#1F2937"));
        titleView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.weight = 1;
        titleView.setLayoutParams(titleParams);
        header.addView(titleView);
        Button addButton = new Button(this);
        addButton.setText("+ Add");
        addButton.setTextSize(14);
        addButton.setTextColor(Color.WHITE);
        addButton.setTypeface(null, Typeface.BOLD);
        GradientDrawable addBtnBg = new GradientDrawable();
        addBtnBg.setShape(GradientDrawable.RECTANGLE);
        addBtnBg.setColor(Color.parseColor("#8B5CF6"));
        addBtnBg.setCornerRadius(12);
        addButton.setBackground(addBtnBg);
        addButton.setElevation(2);
        addButton.setPadding(32, 16, 32, 16);
        addButton.setOnClickListener(v -> showAddContactDialog());
        header.addView(addButton);
        cardContent.addView(header);
        contactsContainer = new LinearLayout(this);
        contactsContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contactsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = 20;
        contactsContainer.setLayoutParams(containerParams);
        cardContent.addView(contactsContainer);
        card.addView(cardContent);
        parent.addView(card);
    }

    private void createFeatureCards(LinearLayout parent) {
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = 32;
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("Safety Features");
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(Color.parseColor("#1F2937"));
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setPadding(8, 0, 0, 16);
        sectionTitle.setLayoutParams(sectionParams);
        parent.addView(sectionTitle);
        String[] features = {"🎙️|Voice Recording|Continuous audio recording during emergency", "📸|Auto Capture|Front & back camera photos every 5 seconds", "📍|Live Location|Real-time GPS tracking every 10 seconds", "🔊|Volume Trigger|Hold Volume Up 6 sec (works when locked)"};
        for (String feature : features) {
            String[] parts = feature.split("\\|");
            CardView card = createCard();
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = 12;
            card.setLayoutParams(cardParams);
            LinearLayout content = new LinearLayout(this);
            content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setPadding(24, 20, 24, 20);
            content.setGravity(Gravity.CENTER_VERTICAL);
            TextView featureIcon = new TextView(this);
            featureIcon.setText(parts[0]);
            featureIcon.setTextSize(28);
            content.addView(featureIcon);
            LinearLayout textLayout = new LinearLayout(this);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.weight = 1;
            textParams.leftMargin = 16;
            textLayout.setLayoutParams(textParams);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            TextView featureTitle = new TextView(this);
            featureTitle.setText(parts[1]);
            featureTitle.setTextSize(16);
            featureTitle.setTextColor(Color.parseColor("#1F2937"));
            featureTitle.setTypeface(null, Typeface.BOLD);
            textLayout.addView(featureTitle);
            TextView featureDesc = new TextView(this);
            featureDesc.setText(parts[2]);
            featureDesc.setTextSize(13);
            featureDesc.setTextColor(Color.parseColor("#6B7280"));
            LinearLayout.LayoutParams fdParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fdParams.topMargin = 4;
            featureDesc.setLayoutParams(fdParams);
            textLayout.addView(featureDesc);
            content.addView(textLayout);
            card.addView(content);
            parent.addView(card);
        }
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
        bottomNavHome = createNavItem("🏠", "Home", true);
        bottomNav.addView(bottomNavHome);
        bottomNavGallery = createNavItem("📸", "Gallery", false);
        bottomNavGallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        bottomNav.addView(bottomNavGallery);
        bottomNavAI = createNavItem("🤖", "AI Tools", false);
        bottomNavAI.setOnClickListener(v -> showAIToolsMenu());
        bottomNav.addView(bottomNavAI);
        bottomNavProfile = createNavItem("👤", "Profile", false);
        bottomNavProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
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

    private CardView createCard() {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setRadius(24);
        card.setCardElevation(8);
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    public void toggleSOS() {
        if (!isSosActive) {
            Set<String> contacts = sharedPreferences.getStringSet("emergency_contacts", new HashSet<>());
            if (contacts == null || contacts.isEmpty()) {
                Toast.makeText(this, "❌ Please add emergency contacts first!", Toast.LENGTH_LONG).show();
                return;
            }
            Intent serviceIntent = new Intent(this, SOSService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            isSosActive = true;
            sosButton.setText("STOP SOS");
            GradientDrawable stopBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#059669"), Color.parseColor("#10B981")});
            stopBg.setCornerRadius(16);
            sosButton.setBackground(stopBg);
            Toast.makeText(this, "🚨 SOS ACTIVATED!", Toast.LENGTH_SHORT).show();
        } else {
            stopService(new Intent(this, SOSService.class));
            isSosActive = false;
            sosButton.setText("ACTIVATE SOS");
            GradientDrawable activateBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#DC2626"), Color.parseColor("#EF4444")});
            activateBg.setCornerRadius(16);
            sosButton.setBackground(activateBg);
            Toast.makeText(this, "SOS Stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Emergency Contact");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Contact Name");
        nameInput.setPadding(30, 30, 30, 30);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setColor(Color.parseColor("#F9FAFB"));
        inputBg.setCornerRadius(12);
        nameInput.setBackground(inputBg);
        layout.addView(nameInput);
        final EditText phoneInput = new EditText(this);
        phoneInput.setHint("Phone Number (10 digits)");
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phoneInput.setPadding(30, 30, 30, 30);
        GradientDrawable phoneBg = new GradientDrawable();
        phoneBg.setShape(GradientDrawable.RECTANGLE);
        phoneBg.setColor(Color.parseColor("#F9FAFB"));
        phoneBg.setCornerRadius(12);
        phoneInput.setBackground(phoneBg);
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        phoneParams.topMargin = 20;
        phoneInput.setLayoutParams(phoneParams);
        layout.addView(phoneInput);
        builder.setView(layout);
        builder.setPositiveButton("Add Contact", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();
            if (!name.isEmpty() && !phone.isEmpty() && phone.length() == 10) {
                addContact(name, phone);
            } else {
                Toast.makeText(this, "Please enter valid name and 10-digit number", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addContact(String name, String phone) {
        Set<String> contacts = sharedPreferences.getStringSet("emergency_contacts", new HashSet<>());
        Set<String> updatedContacts = new HashSet<>(contacts);
        updatedContacts.add(name + ":" + phone);
        sharedPreferences.edit().putStringSet("emergency_contacts", updatedContacts).apply();
        loadContacts();
        Toast.makeText(this, "✅ Contact added successfully", Toast.LENGTH_SHORT).show();
    }

    private void loadContacts() {
        contactsContainer.removeAllViews();
        Set<String> contacts = sharedPreferences.getStringSet("emergency_contacts", new HashSet<>());
        if (contacts == null || contacts.isEmpty()) {
            LinearLayout emptyState = new LinearLayout(this);
            emptyState.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            emptyState.setOrientation(LinearLayout.VERTICAL);
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setPadding(24, 32, 24, 32);
            TextView emptyIcon = new TextView(this);
            emptyIcon.setText("👥");
            emptyIcon.setTextSize(48);
            emptyIcon.setGravity(Gravity.CENTER);
            emptyIcon.setAlpha(0.3f);
            emptyState.addView(emptyIcon);
            TextView emptyText = new TextView(this);
            emptyText.setText("No contacts added yet");
            emptyText.setTextColor(Color.parseColor("#6B7280"));
            emptyText.setTextSize(14);
            emptyText.setTypeface(null, Typeface.BOLD);
            emptyText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = 12;
            emptyText.setLayoutParams(textParams);
            emptyState.addView(emptyText);
            TextView emptySubtext = new TextView(this);
            emptySubtext.setText("Tap '+ Add' to add emergency contacts");
            emptySubtext.setTextColor(Color.parseColor("#9CA3AF"));
            emptySubtext.setTextSize(12);
            emptySubtext.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subtextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subtextParams.topMargin = 4;
            emptySubtext.setLayoutParams(subtextParams);
            emptyState.addView(emptySubtext);
            contactsContainer.addView(emptyState);
            return;
        }
        for (String contact : contacts) {
            String[] parts = contact.split(":");
            if (parts.length == 2) {
                addContactView(parts[0], parts[1]);
            }
        }
    }

    private void addContactView(String name, String phone) {
        LinearLayout contactItem = new LinearLayout(this);
        contactItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contactItem.setOrientation(LinearLayout.HORIZONTAL);
        contactItem.setPadding(20, 20, 20, 20);
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setShape(GradientDrawable.RECTANGLE);
        itemBg.setColor(Color.parseColor("#F9FAFB"));
        itemBg.setCornerRadius(12);
        contactItem.setBackground(itemBg);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.topMargin = 12;
        contactItem.setLayoutParams(itemParams);
        LinearLayout textLayout = new LinearLayout(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.weight = 1;
        textLayout.setLayoutParams(textParams);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(16);
        nameView.setTextColor(Color.parseColor("#1F2937"));
        nameView.setTypeface(null, Typeface.BOLD);
        textLayout.addView(nameView);
        TextView phoneView = new TextView(this);
        phoneView.setText(phone);
        phoneView.setTextSize(14);
        phoneView.setTextColor(Color.parseColor("#6B7280"));
        textLayout.addView(phoneView);
        contactItem.addView(textLayout);
        Button deleteButton = new Button(this);
        deleteButton.setText("Delete");
        deleteButton.setTextSize(12);
        deleteButton.setTextColor(Color.parseColor("#DC2626"));
        deleteButton.setBackgroundColor(Color.TRANSPARENT);
        deleteButton.setOnClickListener(v -> deleteContact(name, phone));
        contactItem.addView(deleteButton);
        contactsContainer.addView(contactItem);
    }

    private void deleteContact(String name, String phone) {
        new AlertDialog.Builder(this).setTitle("Delete Contact").setMessage("Remove " + name + " from emergency contacts?").setPositiveButton("Delete", (dialog, which) -> {
            Set<String> contacts = sharedPreferences.getStringSet("emergency_contacts", new HashSet<>());
            Set<String> updatedContacts = new HashSet<>(contacts);
            updatedContacts.remove(name + ":" + phone);
            sharedPreferences.edit().putStringSet("emergency_contacts", updatedContacts).apply();
            loadContacts();
            Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Cancel", null).show();
    }

    private void showAIToolsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AI Tools");
        String[] options = {"Deepfake Detection", "Crime Risk Analysis"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                Toast.makeText(this, "Deepfake Detection - Coming Soon!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Crime Risk Analysis - Coming Soon!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void checkPermissions() {
        String[] permissions = {Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "✅ All permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Some permissions denied. App may not work properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (volumeDetector != null && volumeDetector.onKeyDown(keyCode, event, this)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (volumeDetector != null && volumeDetector.onKeyUp(keyCode, event, this)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}