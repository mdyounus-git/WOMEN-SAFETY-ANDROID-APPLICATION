package com.example.womensafety;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class LoginActivity extends AppCompatActivity {
    private LinearLayout nameLayout, phoneLayout, confirmPasswordLayout;
    private EditText etName, etPhone, etEmail, etPassword, etConfirmPassword;
    private Button btnAuth;
    private TextView tvFormTitle, tvToggleAction;
    private boolean isLoginMode = true;
    private SharedPrefManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefManager = SharedPrefManager.getInstance(this);
        if (prefManager.isLoggedIn()) {
            navigateToMain();
            return;
        }
        createPremiumUI();
    }

    private void createPremiumUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable mainBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7"), Color.parseColor("#EC4899")});
        mainLayout.setBackground(mainBg);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(32, 60, 32, 32);
        contentLayout.setGravity(Gravity.CENTER);
        createLogoSection(contentLayout);
        createFormCard(contentLayout);
        scrollView.addView(contentLayout);
        mainLayout.addView(scrollView);
        setContentView(mainLayout);
    }

    private void createLogoSection(LinearLayout parent) {
        LinearLayout logoContainer = new LinearLayout(this);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logoParams.bottomMargin = dpToPx(40);
        logoContainer.setLayoutParams(logoParams);
        logoContainer.setOrientation(LinearLayout.VERTICAL);
        logoContainer.setGravity(Gravity.CENTER);
        TextView icon = new TextView(this);
        icon.setText("🛡️");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        logoContainer.addView(icon);
        TextView appName = new TextView(this);
        appName.setText("Women Safety");
        appName.setTextSize(28);
        appName.setTextColor(Color.WHITE);
        appName.setTypeface(null, Typeface.BOLD);
        appName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dpToPx(16);
        appName.setLayoutParams(nameParams);
        logoContainer.addView(appName);
        TextView tagline = new TextView(this);
        tagline.setText("Your Safety, Our Priority");
        tagline.setTextSize(14);
        tagline.setTextColor(Color.parseColor("#F3E8FF"));
        tagline.setGravity(Gravity.CENTER);
        tagline.setAlpha(0.9f);
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagParams.topMargin = dpToPx(8);
        tagline.setLayoutParams(tagParams);
        logoContainer.addView(tagline);
        parent.addView(logoContainer);
    }

    private void createFormCard(LinearLayout parent) {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setRadius(24);
        card.setCardElevation(16);
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(32, 32, 32, 32);
        tvFormTitle = new TextView(this);
        tvFormTitle.setText("Sign In");
        tvFormTitle.setTextSize(24);
        tvFormTitle.setTextColor(Color.parseColor("#1F2937"));
        tvFormTitle.setTypeface(null, Typeface.BOLD);
        tvFormTitle.setGravity(Gravity.CENTER);
        cardContent.addView(tvFormTitle);
        TextView subtitle = new TextView(this);
        subtitle.setText("Welcome back! Please login to continue");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#6B7280"));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dpToPx(8);
        subParams.bottomMargin = dpToPx(24);
        subtitle.setLayoutParams(subParams);
        cardContent.addView(subtitle);
        nameLayout = createInputField("Full Name", false);
        cardContent.addView(nameLayout);
        phoneLayout = createInputField("Phone Number", false);
        cardContent.addView(phoneLayout);
        LinearLayout emailLayout = createInputField("Email", true);
        cardContent.addView(emailLayout);
        LinearLayout passwordLayout = createInputField("Password", true);
        cardContent.addView(passwordLayout);
        confirmPasswordLayout = createInputField("Confirm Password", false);
        cardContent.addView(confirmPasswordLayout);
        btnAuth = new Button(this);
        btnAuth.setText("Sign In");
        btnAuth.setTextSize(16);
        btnAuth.setTextColor(Color.WHITE);
        btnAuth.setTypeface(null, Typeface.BOLD);
        btnAuth.setAllCaps(false);
        btnAuth.setPadding(0, 48, 0, 48);
        GradientDrawable btnBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7")});
        btnBg.setCornerRadius(12);
        btnAuth.setBackground(btnBg);
        btnAuth.setElevation(4);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dpToPx(24);
        btnAuth.setLayoutParams(btnParams);
        btnAuth.setOnClickListener(v -> {
            if (isLoginMode) {
                handleLogin();
            } else {
                handleRegister();
            }
        });
        cardContent.addView(btnAuth);
        LinearLayout toggleContainer = new LinearLayout(this);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.topMargin = dpToPx(24);
        toggleContainer.setLayoutParams(toggleParams);
        toggleContainer.setOrientation(LinearLayout.HORIZONTAL);
        toggleContainer.setGravity(Gravity.CENTER);
        TextView toggleText = new TextView(this);
        toggleText.setText("Don't have an account? ");
        toggleText.setTextSize(14);
        toggleText.setTextColor(Color.parseColor("#6B7280"));
        toggleContainer.addView(toggleText);
        tvToggleAction = new TextView(this);
        tvToggleAction.setText("Sign Up");
        tvToggleAction.setTextSize(14);
        tvToggleAction.setTextColor(Color.parseColor("#8B5CF6"));
        tvToggleAction.setTypeface(null, Typeface.BOLD);
        tvToggleAction.setOnClickListener(v -> toggleMode());
        toggleContainer.addView(tvToggleAction);
        cardContent.addView(toggleContainer);
        card.addView(cardContent);
        parent.addView(card);
    }

    private LinearLayout createInputField(String label, boolean visible) {
        LinearLayout container = new LinearLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerParams.topMargin = dpToPx(16);
        container.setLayoutParams(containerParams);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setVisibility(visible ? LinearLayout.VISIBLE : LinearLayout.GONE);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(Color.parseColor("#374151"));
        labelView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = dpToPx(8);
        labelView.setLayoutParams(labelParams);
        container.addView(labelView);
        EditText editText = new EditText(this);
        editText.setHint(label);
        editText.setPadding(20, 20, 20, 20);
        editText.setTextSize(16);
        editText.setTextColor(Color.parseColor("#1F2937"));
        editText.setHintTextColor(Color.parseColor("#9CA3AF"));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setColor(Color.parseColor("#F9FAFB"));
        inputBg.setStroke(dpToPx(1), Color.parseColor("#E5E7EB"));
        inputBg.setCornerRadius(12);
        editText.setBackground(inputBg);
        if (label.equals("Email")) {
            editText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            etEmail = editText;
        } else if (label.equals("Password") || label.equals("Confirm Password")) {
            editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (label.equals("Password")) {
                etPassword = editText;
            } else {
                etConfirmPassword = editText;
            }
        } else if (label.equals("Phone Number")) {
            editText.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            etPhone = editText;
        } else if (label.equals("Full Name")) {
            editText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
            etName = editText;
        }
        container.addView(editText);
        return container;
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter valid email");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }
        if (!prefManager.isUserRegistered()) {
            Toast.makeText(this, "No account found. Please register first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (prefManager.validateLogin(email, password)) {
            Toast.makeText(this, "✅ Login successful!", Toast.LENGTH_SHORT).show();
            navigateToMain();
        } else {
            Toast.makeText(this, "❌ Invalid email or password", Toast.LENGTH_LONG).show();
        }
    }

    private void handleRegister() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number is required");
            etPhone.requestFocus();
            return;
        }
        if (phone.length() != 10) {
            etPhone.setError("Enter valid 10-digit phone number");
            etPhone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter valid email");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Confirm password is required");
            etConfirmPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }
        if (prefManager.isUserRegistered()) {
            Toast.makeText(this, "User already registered. Please login.", Toast.LENGTH_LONG).show();
            toggleMode();
            return;
        }
        prefManager.saveUser(name, email, phone, password);
        Toast.makeText(this, "✅ Registration successful!", Toast.LENGTH_SHORT).show();
        navigateToMain();
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            tvFormTitle.setText("Sign In");
            btnAuth.setText("Sign In");
            tvToggleAction.setText("Sign Up");
            nameLayout.setVisibility(LinearLayout.GONE);
            phoneLayout.setVisibility(LinearLayout.GONE);
            confirmPasswordLayout.setVisibility(LinearLayout.GONE);
        } else {
            tvFormTitle.setText("Sign Up");
            btnAuth.setText("Sign Up");
            tvToggleAction.setText("Sign In");
            nameLayout.setVisibility(LinearLayout.VISIBLE);
            phoneLayout.setVisibility(LinearLayout.VISIBLE);
            confirmPasswordLayout.setVisibility(LinearLayout.VISIBLE);
        }
        etName.setText("");
        etPhone.setText("");
        etEmail.setText("");
        etPassword.setText("");
        etConfirmPassword.setText("");
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}