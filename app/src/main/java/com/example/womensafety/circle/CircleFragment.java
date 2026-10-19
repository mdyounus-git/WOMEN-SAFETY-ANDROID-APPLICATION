package com.example.womensafety.circle;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.womensafety.R;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.DialogCodeBinding;
import com.example.womensafety.databinding.FragmentCircleBinding;
import com.example.womensafety.databinding.ItemPersonBinding;
import com.example.womensafety.guardian.LiveViewActivity;
import com.example.womensafety.location.LocationShareService;
import com.example.womensafety.ui.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The circle: who can see me (and the QR to add them), and the people I look after (scan their
 * QR, open their live view).
 */
public class CircleFragment extends Fragment {

    private FragmentCircleBinding b;
    private String uid;
    private final List<CircleRepository.Subscription> subs = new ArrayList<>();
    /** Per-ward listeners on live/{ward}: SOS flag + last update time. */
    private final Map<DatabaseReference, ValueEventListener> wardListeners = new HashMap<>();

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) handleCode(result.getContents());
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCircleBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.padForSystemBars(b.column, true, true);
        boolean configured = Backend.isConfigured(requireContext());
        b.notConfigured.setVisibility(configured ? View.GONE : View.VISIBLE);
        b.addGuardian.setEnabled(configured);
        b.scan.setEnabled(configured);
        b.enterCode.setEnabled(configured);

        b.addGuardian.setOnClickListener(v -> startActivity(new Intent(requireContext(), InviteActivity.class)));
        b.scan.setOnClickListener(v -> startScan());
        b.enterCode.setOnClickListener(v -> askForCode());

        Prefs prefs = Prefs.get(requireContext());
        b.sharingSwitch.setChecked(prefs.isSharingEnabled());
        b.sharingSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setSharingEnabled(checked);
            LocationShareService.sync(requireContext());
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!Backend.isConfigured(requireContext())) return;
        Backend.ensureSignedIn(requireContext(), new Backend.UidCallback() {
            @Override
            public void onReady(@NonNull String id) {
                if (b == null) return;
                uid = id;
                subs.add(CircleRepository.listenGuardians(requireContext(), id, CircleFragment.this::showGuardians));
                subs.add(CircleRepository.listenWatching(requireContext(), id, CircleFragment.this::showWards));
                checkPendingJoin();
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (b != null) Toast.makeText(requireContext(), R.string.error_offline, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onStop() {
        for (CircleRepository.Subscription s : subs) s.remove();
        subs.clear();
        clearWardListeners();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /** Handles a code from a link that opened the app (see MainActivity). */
    public void checkPendingJoin() {
        if (uid == null || !(getActivity() instanceof MainActivity)) return;
        String code = ((MainActivity) getActivity()).consumePendingJoinCode();
        if (code != null) handleCode(code);
    }

    // ------------------------------------------------------------------ guardians

    private void showGuardians(List<Person> people) {
        if (b == null) return;
        b.guardiansList.removeAllViews();
        b.guardiansEmpty.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
        for (Person p : people) {
            ItemPersonBinding row = ItemPersonBinding.inflate(getLayoutInflater(), b.guardiansList, false);
            row.avatar.setText(Ui.initials(p.name));
            row.name.setText(p.name);
            row.subtitle.setText(R.string.circle_guardian_sub);
            row.action.setIconResource(R.drawable.ic_close);
            row.action.setContentDescription(getString(R.string.circle_remove_desc, p.name));
            row.action.setOnClickListener(v -> confirmRemove(p));
            b.guardiansList.addView(row.getRoot());
        }
    }

    private void confirmRemove(Person p) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.circle_remove_title, p.name))
                .setMessage(getString(R.string.circle_remove_text, p.name))
                .setPositiveButton(R.string.action_remove, (d, w) ->
                        CircleRepository.removeGuardian(uid, p.uid, new CircleRepository.Result<Void>() {
                            @Override
                            public void onSuccess(Void value) {
                            }

                            @Override
                            public void onError(@NonNull Exception e) {
                                if (b != null) Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ wards

    private void showWards(List<Person> people) {
        if (b == null) return;
        clearWardListeners();
        b.wardsList.removeAllViews();
        b.wardsEmpty.setVisibility(people.isEmpty() ? View.VISIBLE : View.GONE);
        for (Person p : people) {
            ItemPersonBinding row = ItemPersonBinding.inflate(getLayoutInflater(), b.wardsList, false);
            row.avatar.setText(Ui.initials(p.name));
            row.name.setText(p.name);
            row.subtitle.setText(R.string.circle_ward_loading);
            row.action.setIconResource(R.drawable.ic_chevron_right);
            row.action.setContentDescription(getString(R.string.circle_open_desc, p.name));
            View.OnClickListener open = v -> startActivity(LiveViewActivity.intent(requireContext(), p.uid, p.name));
            row.action.setOnClickListener(open);
            row.getRoot().setOnClickListener(open);
            row.getRoot().setOnLongClickListener(v -> {
                confirmLeave(p);
                return true;
            });
            b.wardsList.addView(row.getRoot());
            watchWard(p, row);
        }
    }

    private void watchWard(Person p, ItemPersonBinding row) {
        DatabaseReference ref = Backend.live(p.uid);
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (b == null) return;
                boolean sos = Boolean.TRUE.equals(s.child("sos").child("active").getValue(Boolean.class));
                Long ts = s.child("location").child("ts").getValue(Long.class);
                row.badge.setVisibility(sos ? View.VISIBLE : View.GONE);
                row.badge.setText(R.string.badge_sos);
                row.avatar.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(),
                        sos ? R.color.ws_sos_container : R.color.ws_plum_container));
                row.subtitle.setText(sos ? getString(R.string.circle_ward_sos)
                        : ts == null ? getString(R.string.circle_ward_no_location)
                        : getString(R.string.circle_ward_updated, Ui.relativeTime(requireContext(), ts)));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (b == null) return;
                row.subtitle.setText(R.string.circle_ward_removed);
            }
        };
        ref.addValueEventListener(l);
        wardListeners.put(ref, l);
    }

    private void clearWardListeners() {
        for (Map.Entry<DatabaseReference, ValueEventListener> e : wardListeners.entrySet()) {
            e.getKey().removeEventListener(e.getValue());
        }
        wardListeners.clear();
    }

    private void confirmLeave(Person p) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.circle_leave_title, p.name))
                .setMessage(getString(R.string.circle_leave_text, p.name))
                .setPositiveButton(R.string.action_remove, (d, w) ->
                        CircleRepository.leave(uid, p.uid, new CircleRepository.Result<Void>() {
                            @Override
                            public void onSuccess(Void value) {
                            }

                            @Override
                            public void onError(@NonNull Exception e) {
                            }
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ joining

    private void startScan() {
        ScanOptions options = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.circle_scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false);
        scanner.launch(options);
    }

    private void askForCode() {
        DialogCodeBinding d = DialogCodeBinding.inflate(getLayoutInflater());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.circle_enter_code_title)
                .setView(d.getRoot())
                .setPositiveButton(R.string.action_continue, (dialog, w) -> {
                    CharSequence t = d.code.getText();
                    handleCode(t == null ? "" : t.toString().replaceAll("[\\s·-]", ""));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void handleCode(@NonNull String raw) {
        String code = CircleRepository.parseCode(raw);
        if (code == null) {
            Toast.makeText(requireContext(), R.string.join_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        CircleRepository.lookupInvite(requireContext(), code, new CircleRepository.Result<CircleRepository.InviteInfo>() {
            @Override
            public void onSuccess(CircleRepository.InviteInfo info) {
                if (b != null) confirmJoin(info);
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (b == null) return;
                String m = e.getMessage();
                int msg = "expired".equals(m) ? R.string.join_expired
                        : "self".equals(m) ? R.string.join_self
                        : "not_found".equals(m) ? R.string.join_not_found : R.string.error_offline;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmJoin(CircleRepository.InviteInfo info) {
        String name = TextUtils.isEmpty(info.wardName) ? getString(R.string.someone_in_circle) : info.wardName;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.join_title, name))
                .setMessage(getString(R.string.join_text, name))
                .setPositiveButton(R.string.join_confirm, (d, w) ->
                        CircleRepository.acceptInvite(requireContext(), info, new CircleRepository.Result<Void>() {
                            @Override
                            public void onSuccess(Void value) {
                                if (b != null) {
                                    Toast.makeText(requireContext(), getString(R.string.join_done, name),
                                            Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onError(@NonNull Exception e) {
                                if (b != null) {
                                    Toast.makeText(requireContext(), R.string.join_failed, Toast.LENGTH_LONG).show();
                                }
                            }
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
