package com.example.womensafety.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.womensafety.R;
import com.example.womensafety.core.PhoneNumbers;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivityOnboardingBinding;

/**
 * First run: a short welcome and two fields. Replaces the old email/password login — there was
 * no server, so the password only protected data that was on the same phone, and it was stored
 * in plain text. Identity for live sharing now comes from Firebase Anonymous Auth.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_WomenSafety);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.column, true, true);

        Prefs prefs = Prefs.get(this);
        b.name.setText(prefs.getName());
        b.phone.setText(prefs.getPhone());
        b.next.setOnClickListener(v -> submit());
        b.phone.setOnEditorActionListener((v, actionId, event) -> {
            submit();
            return true;
        });
    }

    private void submit() {
        String name = text(b.name.getText());
        String phone = text(b.phone.getText());
        boolean ok = true;
        if (name.length() < 2) {
            b.nameLayout.setError(getString(R.string.error_name));
            ok = false;
        } else {
            b.nameLayout.setError(null);
        }
        if (!PhoneNumbers.isValid(phone)) {
            b.phoneLayout.setError(getString(R.string.error_phone));
            ok = false;
        } else {
            b.phoneLayout.setError(null);
        }
        if (!ok) return;

        Prefs prefs = Prefs.get(this);
        prefs.saveProfile(name, PhoneNumbers.clean(phone));
        prefs.setOnboarded(true);
        startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_FIRST_RUN, true));
        finish();
    }

    private static String text(@Nullable Editable e) {
        return e == null ? "" : e.toString().trim();
    }
}
