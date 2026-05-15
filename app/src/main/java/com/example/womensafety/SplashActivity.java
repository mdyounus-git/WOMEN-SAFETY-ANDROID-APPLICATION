package com.example.womensafety;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER);
        
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7"), Color.parseColor("#EC4899")});
        mainLayout.setBackground(gradient);

        LinearLayout iconContainer = new LinearLayout(this);
        LinearLayout.LayoutParams iconContainerParams = new LinearLayout.LayoutParams(dpToPx(160), dpToPx(160));
        iconContainer.setLayoutParams(iconContainerParams);
        iconContainer.setGravity(Gravity.CENTER);
        
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(Color.parseColor("#FFFFFF"));
        iconBg.setAlpha(40);
        iconContainer.setBackground(iconBg);
        iconContainer.setElevation(dpToPx(8));

        TextView iconView = new TextView(this);
        iconView.setText("🛡️");
        iconView.setTextSize(72);
        iconView.setGravity(Gravity.CENTER);
        iconContainer.addView(iconView);

        mainLayout.addView(iconContainer);

        TextView appName = new TextView(this);
        appName.setText("Women Safety");
        appName.setTextSize(32);
        appName.setTextColor(Color.WHITE);
        appName.setTypeface(null, Typeface.BOLD);
        appName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dpToPx(32);
        appName.setLayoutParams(nameParams);
        mainLayout.addView(appName);

        TextView tagline = new TextView(this);
        tagline.setText("Your Safety, Our Priority");
        tagline.setTextSize(16);
        tagline.setTextColor(Color.parseColor("#F3E8FF"));
        tagline.setGravity(Gravity.CENTER);
        tagline.setAlpha(0.9f);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        taglineParams.topMargin = dpToPx(12);
        tagline.setLayoutParams(taglineParams);
        mainLayout.addView(tagline);

        TextView loadingDots = new TextView(this);
        loadingDots.setText("•••");
        loadingDots.setTextSize(24);
        loadingDots.setTextColor(Color.WHITE);
        loadingDots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsParams.topMargin = dpToPx(48);
        loadingDots.setLayoutParams(dotsParams);
        mainLayout.addView(loadingDots);

        setContentView(mainLayout);

        ValueAnimator pulseAnimator = ValueAnimator.ofFloat(0.5f, 1.0f);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            iconContainer.setScaleX(value);
            iconContainer.setScaleY(value);
            iconContainer.setAlpha(0.7f + (value * 0.3f));
        });
        pulseAnimator.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
                Intent intent;
                
                if (prefManager.isLoggedIn()) {
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                } else {
                    intent = new Intent(SplashActivity.this, LoginActivity.class);
                }
                
                startActivity(intent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        }, 2500);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}