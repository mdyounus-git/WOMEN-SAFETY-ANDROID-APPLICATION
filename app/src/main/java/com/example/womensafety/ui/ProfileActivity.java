package com.example.womensafety.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.womensafety.BuildConfig;
import com.example.womensafety.R;
import com.example.womensafety.circle.CircleRepository;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.EmergencyContact;
import com.example.womensafety.core.PhoneNumbers;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivityProfileBinding;
import com.example.womensafety.databinding.DialogPersonBinding;
import com.example.womensafety.databinding.ItemPersonBinding;
import com.example.womensafety.evidence.EvidenceActivity;
import com.example.womensafety.guardian.GuardianWatchService;
import com.example.womensafety.location.LocationShareService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Profile, SMS emergency contacts, and shortcuts. */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.column, true, true);

        b.back.setOnClickListener(v -> finish());
        b.edit.setOnClickListener(v -> editProfile());
        b.addContact.setOnClickListener(v -> addContact());
        b.openSetup.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        b.openEvidence.setOnClickListener(v -> startActivity(new Intent(this, EvidenceActivity.class)));
        b.testVolume.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_volume_help)
                .setMessage(R.string.profile_volume_help_text)
                .setPositiveButton(R.string.action_ok, null)
                .show());
        b.reset.setOnClickListener(v -> confirmReset());
        b.version.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        Prefs prefs = Prefs.get(this);
        b.name.setText(prefs.getName());
        b.phone.setText(prefs.getPhone().isEmpty() ? "" : PhoneNumbers.toInternational(prefs.getPhone()));
        b.avatar.setText(Ui.initials(prefs.getName()));

        b.contactsList.removeAllViews();
        java.util.List<EmergencyContact> contacts = prefs.getContacts();
        b.contactsEmpty.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
        for (EmergencyContact c : contacts) {
            ItemPersonBinding row = ItemPersonBinding.inflate(getLayoutInflater(), b.contactsList, false);
            row.avatar.setText(Ui.initials(c.getName()));
            row.name.setText(c.getName());
            row.subtitle.setText(c.getDialableNumber());
            row.action.setIconResource(R.drawable.ic_delete);
            row.action.setContentDescription(getString(R.string.circle_remove_desc, c.getName()));
            row.action.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.profile_remove_contact, c.getName()))
                    .setPositiveButton(R.string.action_remove, (d, w) -> {
                        Prefs.get(this).removeContact(c);
                        render();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show());
            b.contactsList.addView(row.getRoot());
        }
    }

    private void editProfile() {
        Prefs prefs = Prefs.get(this);
        showPersonDialog(R.string.profile_edit, prefs.getName(), prefs.getPhone(), (name, phone) -> {
            prefs.saveProfile(name, phone);
            String uid = Backend.uid(this);
            if (uid != null) CircleRepository.publishProfile(this, uid);
            render();
        });
    }

    private void addContact() {
        showPersonDialog(R.string.profile_add_contact, "", "", (name, phone) -> {
            Prefs.get(this).addContact(new EmergencyContact(name, phone));
            render();
        });
    }

    private interface PersonCallback {
        void onSaved(String name, String phone);
    }

    /** Name + phone dialog with validation that keeps the dialog open on errors. */
    private void showPersonDialog(int title, String name, String phone, PersonCallback callback) {
        DialogPersonBinding d = DialogPersonBinding.inflate(getLayoutInflater());
        d.name.setText(name);
        d.phone.setText(phone);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(d.getRoot())
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.setOnShowListener(x -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                String n = text(d.name.getText());
                String p = text(d.phone.getText());
                d.nameLayout.setError(n.length() < 2 ? getString(R.string.error_name) : null);
                d.phoneLayout.setError(PhoneNumbers.isValid(p) ? null : getString(R.string.error_phone));
                if (n.length() < 2 || !PhoneNumbers.isValid(p)) return;
                callback.onSaved(n, PhoneNumbers.clean(p));
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_reset_title)
                .setMessage(R.string.profile_reset_text)
                .setPositiveButton(R.string.profile_reset_confirm, (d, w) -> {
                    Prefs.get(this).clearAll();
                    stopService(new Intent(this, LocationShareService.class));
                    stopService(new Intent(this, GuardianWatchService.class));
                    Backend.signOut(this);
                    startActivity(new Intent(this, OnboardingActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String text(@Nullable Editable e) {
        return e == null ? "" : e.toString().trim();
    }
}
