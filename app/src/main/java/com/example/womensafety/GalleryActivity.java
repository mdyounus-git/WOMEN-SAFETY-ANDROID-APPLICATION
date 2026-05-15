package com.example.womensafety;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {
    private LinearLayout photosContainer, audioContainer;
    private MediaPlayer mediaPlayer;
    private Handler seekBarHandler;
    private Runnable updateSeekBar;
    private LinearLayout bottomNavHome, bottomNavGallery, bottomNavAI, bottomNavProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        seekBarHandler = new Handler();
        createPremiumUI();
        loadGalleryContent();
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
        createPhotosSection(contentLayout);
        createAudioSection(contentLayout);
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
        title.setText("SOS Gallery");
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

    private void createPhotosSection(LinearLayout parent) {
        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setPadding(8, 0, 8, 16);
        TextView sectionIcon = new TextView(this);
        sectionIcon.setText("📸");
        sectionIcon.setTextSize(24);
        sectionHeader.addView(sectionIcon);
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("  Captured Photos");
        sectionTitle.setTextSize(20);
        sectionTitle.setTextColor(Color.parseColor("#1F2937"));
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionHeader.addView(sectionTitle);
        parent.addView(sectionHeader);
        CardView photosCard = createPremiumCard();
        LinearLayout photosContent = new LinearLayout(this);
        photosContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        photosContent.setOrientation(LinearLayout.VERTICAL);
        photosContent.setPadding(24, 24, 24, 24);
        photosContainer = new LinearLayout(this);
        photosContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        photosContainer.setOrientation(LinearLayout.VERTICAL);
        photosContent.addView(photosContainer);
        photosCard.addView(photosContent);
        parent.addView(photosCard);
    }

    private void createAudioSection(LinearLayout parent) {
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = dpToPx(32);
        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeader.setPadding(8, 0, 8, 16);
        TextView sectionIcon = new TextView(this);
        sectionIcon.setText("🎙️");
        sectionIcon.setTextSize(24);
        sectionHeader.addView(sectionIcon);
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText("  Audio Recordings");
        sectionTitle.setTextSize(20);
        sectionTitle.setTextColor(Color.parseColor("#1F2937"));
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionHeader.addView(sectionTitle);
        sectionHeader.setLayoutParams(sectionParams);
        parent.addView(sectionHeader);
        CardView audioCard = createPremiumCard();
        LinearLayout audioContent = new LinearLayout(this);
        audioContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        audioContent.setOrientation(LinearLayout.VERTICAL);
        audioContent.setPadding(24, 24, 24, 24);
        audioContainer = new LinearLayout(this);
        audioContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        audioContainer.setOrientation(LinearLayout.VERTICAL);
        audioContent.addView(audioContainer);
        audioCard.addView(audioContent);
        parent.addView(audioCard);
    }

    private void loadGalleryContent() {
        loadPhotos();
        loadAudio();
    }

    private void loadPhotos() {
        File photoDir = new File(getExternalFilesDir(null), "SOSPhotos");
        if (!photoDir.exists() || !photoDir.isDirectory() || photoDir.listFiles() == null || photoDir.listFiles().length == 0) {
            addEmptyState(photosContainer, "📷", "No photos captured yet", "Photos will appear here when SOS is activated");
        } else {
            File[] photos = photoDir.listFiles();
            Arrays.sort(photos, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            TextView count = new TextView(this);
            count.setText(photos.length + " photos saved");
            count.setTextSize(14);
            count.setTextColor(Color.parseColor("#6B7280"));
            photosContainer.addView(count);
            for (File photo : photos) {
                addPhotoItem(photo);
            }
        }
    }

    private void addPhotoItem(File photoFile) {
        LinearLayout item = new LinearLayout(this);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.topMargin = dpToPx(12);
        item.setLayoutParams(itemParams);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(16, 16, 16, 16);
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setShape(GradientDrawable.RECTANGLE);
        itemBg.setColor(Color.parseColor("#F9FAFB"));
        itemBg.setCornerRadius(12);
        item.setBackground(itemBg);
        ImageView thumbnail = new ImageView(this);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dpToPx(60), dpToPx(60));
        thumbnail.setLayoutParams(thumbParams);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.RECTANGLE);
        thumbBg.setCornerRadius(8);
        thumbnail.setBackground(thumbBg);
        thumbnail.setClipToOutline(true);
        Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
        if (bitmap != null) {
            thumbnail.setImageBitmap(bitmap);
        }
        item.addView(thumbnail);
        LinearLayout textLayout = new LinearLayout(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.weight = 1;
        textParams.leftMargin = dpToPx(12);
        textLayout.setLayoutParams(textParams);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = new TextView(this);
        nameView.setText(photoFile.getName());
        nameView.setTextSize(14);
        nameView.setTextColor(Color.parseColor("#1F2937"));
        nameView.setTypeface(null, Typeface.BOLD);
        textLayout.addView(nameView);
        TextView dateView = new TextView(this);
        dateView.setText(formatDate(photoFile.lastModified()));
        dateView.setTextSize(12);
        dateView.setTextColor(Color.parseColor("#6B7280"));
        textLayout.addView(dateView);
        item.addView(textLayout);
        LinearLayout actions = new LinearLayout(this);
        actions.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView viewBtn = createActionButton("👁️");
        viewBtn.setOnClickListener(v -> viewPhotoFullScreen(photoFile));
        actions.addView(viewBtn);
        TextView shareBtn = createActionButton("📤");
        shareBtn.setOnClickListener(v -> sharePhoto(photoFile));
        actions.addView(shareBtn);
        TextView deleteBtn = createActionButton("🗑️");
        deleteBtn.setOnClickListener(v -> confirmDeletePhoto(photoFile));
        actions.addView(deleteBtn);
        item.addView(actions);
        photosContainer.addView(item);
    }

    private void loadAudio() {
        File audioDir = new File(getExternalFilesDir(null), "SOSAudio");
        if (!audioDir.exists() || !audioDir.isDirectory() || audioDir.listFiles() == null || audioDir.listFiles().length == 0) {
            addEmptyState(audioContainer, "🎵", "No audio recordings yet", "Audio will be recorded when SOS is activated");
        } else {
            File[] audioFiles = audioDir.listFiles();
            Arrays.sort(audioFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            TextView count = new TextView(this);
            count.setText(audioFiles.length + " recordings saved");
            count.setTextSize(14);
            count.setTextColor(Color.parseColor("#6B7280"));
            audioContainer.addView(count);
            for (File audio : audioFiles) {
                addAudioItem(audio);
            }
        }
    }

    private void addAudioItem(File audioFile) {
        LinearLayout item = new LinearLayout(this);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.topMargin = dpToPx(12);
        item.setLayoutParams(itemParams);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(16, 16, 16, 16);
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setShape(GradientDrawable.RECTANGLE);
        itemBg.setColor(Color.parseColor("#F9FAFB"));
        itemBg.setCornerRadius(12);
        item.setBackground(itemBg);
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout textLayout = new LinearLayout(this);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.weight = 1;
        textLayout.setLayoutParams(textParams);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = new TextView(this);
        nameView.setText(audioFile.getName());
        nameView.setTextSize(14);
        nameView.setTextColor(Color.parseColor("#1F2937"));
        nameView.setTypeface(null, Typeface.BOLD);
        textLayout.addView(nameView);
        TextView dateView = new TextView(this);
        dateView.setText(formatDate(audioFile.lastModified()));
        dateView.setTextSize(12);
        dateView.setTextColor(Color.parseColor("#6B7280"));
        textLayout.addView(dateView);
        headerRow.addView(textLayout);
        LinearLayout actions = new LinearLayout(this);
        actions.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView shareBtn = createActionButton("📤");
        shareBtn.setOnClickListener(v -> shareAudio(audioFile));
        actions.addView(shareBtn);
        TextView deleteBtn = createActionButton("🗑️");
        deleteBtn.setOnClickListener(v -> confirmDeleteAudio(audioFile));
        actions.addView(deleteBtn);
        headerRow.addView(actions);
        item.addView(headerRow);
        LinearLayout playbackLayout = new LinearLayout(this);
        LinearLayout.LayoutParams playbackParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playbackParams.topMargin = dpToPx(12);
        playbackLayout.setLayoutParams(playbackParams);
        playbackLayout.setOrientation(LinearLayout.HORIZONTAL);
        playbackLayout.setGravity(Gravity.CENTER_VERTICAL);
        TextView playBtn = new TextView(this);
        playBtn.setText("▶️");
        playBtn.setTextSize(24);
        playBtn.setPadding(12, 12, 12, 12);
        playBtn.setOnClickListener(v -> playAudio(audioFile, playBtn));
        playbackLayout.addView(playBtn);
        SeekBar seekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.weight = 1;
        seekParams.leftMargin = dpToPx(8);
        seekParams.rightMargin = dpToPx(8);
        seekBar.setLayoutParams(seekParams);
        playbackLayout.addView(seekBar);
        TextView timeView = new TextView(this);
        timeView.setText("0:00");
        timeView.setTextSize(12);
        timeView.setTextColor(Color.parseColor("#6B7280"));
        playbackLayout.addView(timeView);
        item.addView(playbackLayout);
        audioContainer.addView(item);
    }

    private void playAudio(File audioFile, TextView playBtn) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            playBtn.setText("⏸️");
            mediaPlayer.setOnCompletionListener(mp -> playBtn.setText("▶️"));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView createActionButton(String emoji) {
        TextView btn = new TextView(this);
        btn.setText(emoji);
        btn.setTextSize(20);
        btn.setPadding(12, 12, 12, 12);
        return btn;
    }

    private void addEmptyState(LinearLayout parent, String icon, String title, String subtitle) {
        LinearLayout emptyState = new LinearLayout(this);
        emptyState.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(32, 48, 32, 48);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(56);
        iconView.setGravity(Gravity.CENTER);
        iconView.setAlpha(0.3f);
        emptyState.addView(iconView);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(Color.parseColor("#6B7280"));
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dpToPx(16);
        titleView.setLayoutParams(titleParams);
        emptyState.addView(titleView);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(14);
        subtitleView.setTextColor(Color.parseColor("#9CA3AF"));
        subtitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dpToPx(8);
        subtitleView.setLayoutParams(subtitleParams);
        emptyState.addView(subtitleView);
        parent.addView(emptyState);
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
        bottomNavGallery = createNavItem("📸", "Gallery", true);
        bottomNav.addView(bottomNavGallery);
        bottomNavAI = createNavItem("🤖", "AI Tools", false);
        bottomNav.addView(bottomNavAI);
        bottomNavProfile = createNavItem("👤", "Profile", false);
        bottomNavProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
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

    private void viewPhotoFullScreen(File photoFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", photoFile);
        intent.setDataAndType(photoUri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void sharePhoto(File photoFile) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", photoFile);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share Photo"));
    }

    private void confirmDeletePhoto(File photoFile) {
        new AlertDialog.Builder(this).setTitle("Delete Photo").setMessage("Are you sure you want to delete this photo?").setPositiveButton("Delete", (dialog, which) -> {
            if (photoFile.delete()) {
                Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show();
                photosContainer.removeAllViews();
                loadPhotos();
            } else {
                Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void shareAudio(File audioFile) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        Uri audioUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", audioFile);
        shareIntent.setType("audio/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, audioUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share Audio"));
    }

    private void confirmDeleteAudio(File audioFile) {
        new AlertDialog.Builder(this).setTitle("Delete Audio").setMessage("Are you sure you want to delete this recording?").setPositiveButton("Delete", (dialog, which) -> {
            if (audioFile.delete()) {
                Toast.makeText(this, "Audio deleted", Toast.LENGTH_SHORT).show();
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                audioContainer.removeAllViews();
                loadAudio();
            } else {
                Toast.makeText(this, "Failed to delete audio", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (seekBarHandler != null) {
            seekBarHandler.removeCallbacks(updateSeekBar);
        }
    }
}