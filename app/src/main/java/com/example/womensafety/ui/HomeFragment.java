package com.example.womensafety.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.womensafety.R;
import com.example.womensafety.circle.CircleRepository;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.FragmentHomeBinding;
import com.example.womensafety.databinding.ViewStatBinding;
import com.example.womensafety.databinding.ViewTileBinding;
import com.example.womensafety.evidence.EvidenceActivity;
import com.example.womensafety.evidence.EvidenceStore;
import com.example.womensafety.sos.SosState;
import com.example.womensafety.sos.SosTrigger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

/** Home: the SOS button, live status, and shortcuts to everything else. */
public class HomeFragment extends Fragment implements SosState.Observer {

    private FragmentHomeBinding b;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable
    private CircleRepository.Subscription guardiansSub;
    private int guardianCount = -1;
    private SosState.Status status = SosState.current();

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            renderTimer();
            main.postDelayed(this, 1_000L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.padForSystemBars(b.column, true, true);
        if (savedInstanceState == null) Ui.staggerIn(b.column);

        b.avatar.setOnClickListener(v -> startActivity(new Intent(requireContext(), ProfileActivity.class)));
        b.setupCard.setOnClickListener(v -> startActivity(new Intent(requireContext(), SetupActivity.class)));

        setupTile(b.tileCircle, R.drawable.ic_group, R.string.tile_circle,
                v -> ((MainActivity) requireActivity()).selectTab(R.id.nav_circle));
        setupTile(b.tileMap, R.drawable.ic_map, R.string.tile_map,
                v -> ((MainActivity) requireActivity()).selectTab(R.id.nav_map));
        setupTile(b.tileContacts, R.drawable.ic_sms, R.string.tile_contacts,
                v -> startActivity(new Intent(requireContext(), ProfileActivity.class)));
        setupTile(b.tileEvidence, R.drawable.ic_lock, R.string.tile_evidence,
                v -> startActivity(new Intent(requireContext(), EvidenceActivity.class)));
        b.tileMap.subtitle.setText(R.string.tile_map_sub);

        b.statViewers.label.setText(R.string.stat_watching);
        b.statPhotos.label.setText(R.string.stat_photos);
        b.statClips.label.setText(R.string.stat_clips);
        b.statSms.label.setText(R.string.stat_sms);

        b.sosButton.setListener(new SosButton.Listener() {
            @Override
            public void onHoldStart() {
                if (b != null) b.sosHint.setText(status.active ? R.string.sos_hint_stopping : R.string.sos_hint_sending);
            }

            @Override
            public void onHoldCancelled() {
                onSosStatus(status);
            }

            @Override
            public void onHoldComplete() {
                toggleSos();
            }

            @Override
            public void onAccessibleActivate() {
                confirmSos();
            }
        });
        String[] tips = getResources().getStringArray(R.array.safety_tips);
        b.tip.setText(tips[new Random().nextInt(tips.length)]);
    }

    private void setupTile(ViewTileBinding tile, int icon, int title, View.OnClickListener click) {
        tile.icon.setImageResource(icon);
        tile.title.setText(title);
        tile.getRoot().setOnClickListener(click);
    }

    @Override
    public void onStart() {
        super.onStart();
        SosState.observe(this);
        renderHeader();
        renderSetup();
        renderTiles();
        renderPills();
        watchGuardians();
    }

    @Override
    public void onStop() {
        SosState.remove(this);
        main.removeCallbacks(timerTick);
        if (guardiansSub != null) guardiansSub.remove();
        guardiansSub = null;
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    // ------------------------------------------------------------------ rendering

    private void renderHeader() {
        Prefs prefs = Prefs.get(requireContext());
        String first = prefs.getFirstName();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greet = hour < 5 ? R.string.greeting_night : hour < 12 ? R.string.greeting_morning
                : hour < 17 ? R.string.greeting_afternoon : hour < 21 ? R.string.greeting_evening
                : R.string.greeting_night;
        b.greeting.setText(first.isEmpty() ? getString(greet) + "."
                : getString(R.string.greeting_with_name, getString(greet), first));
        b.date.setText(DateUtils.formatDateTime(requireContext(), System.currentTimeMillis(),
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR));
        b.avatar.setText(Ui.initials(prefs.getName()));
    }

    private void renderSetup() {
        List<SetupChecklist.Item> items = SetupChecklist.items(requireContext());
        int done = SetupChecklist.doneCount(items);
        if (done == items.size()) {
            b.setupCard.setVisibility(View.GONE);
            return;
        }
        b.setupCard.setVisibility(View.VISIBLE);
        b.setupProgress.setMax(items.size());
        b.setupProgress.setProgress(done);
        b.setupTitle.setText(R.string.setup_card_title);
        String next = "";
        for (SetupChecklist.Item i : items) {
            if (!i.done) {
                next = getString(i.title);
                break;
            }
        }
        b.setupText.setText(getString(R.string.setup_card_text, done, items.size(), next));
    }

    private void renderTiles() {
        int contacts = Prefs.get(requireContext()).getContacts().size();
        b.tileContacts.subtitle.setText(contacts == 0 ? getString(R.string.tile_contacts_none)
                : getResources().getQuantityString(R.plurals.tile_contacts_count, contacts, contacts));
        int sessions = EvidenceStore.listSessions(requireContext()).size();
        b.tileEvidence.subtitle.setText(sessions == 0 ? getString(R.string.tile_evidence_none)
                : getResources().getQuantityString(R.plurals.tile_evidence_count, sessions, sessions));
        renderCircleTile();
    }

    private void renderCircleTile() {
        if (b == null) return;
        if (!Backend.isConfigured(requireContext())) {
            b.tileCircle.subtitle.setText(R.string.tile_circle_not_setup);
        } else if (guardianCount <= 0) {
            b.tileCircle.subtitle.setText(R.string.tile_circle_none);
        } else {
            b.tileCircle.subtitle.setText(getResources().getQuantityString(
                    R.plurals.tile_circle_count, guardianCount, guardianCount));
        }
    }

    private void renderPills() {
        if (b == null) return;
        android.content.Context c = requireContext();
        Prefs prefs = Prefs.get(c);
        int dot;
        String text;
        if (!Backend.isConfigured(c)) {
            text = getString(R.string.pill_sharing_not_setup);
            dot = R.color.ws_ink_faint;
        } else if (guardianCount == 0 || (guardianCount < 0 && !prefs.hasGuardians())) {
            text = getString(R.string.pill_sharing_nobody);
            dot = R.color.ws_ink_faint;
        } else if (!prefs.isSharingEnabled()) {
            text = getString(R.string.pill_sharing_paused);
            dot = R.color.ws_warn;
        } else if (guardianCount < 0) {
            text = getString(R.string.pill_sharing_on_generic); // count still loading
            dot = R.color.ws_safe;
        } else {
            text = getResources().getQuantityString(R.plurals.pill_sharing_on, guardianCount, guardianCount);
            dot = R.color.ws_safe;
        }
        b.pillSharing.setText(text);
        b.pillSharing.setCompoundDrawableTintList(ContextCompat.getColorStateList(c, dot));

        boolean volume = Permissions.isVolumeTriggerEnabled(c);
        b.pillVolume.setText(volume ? R.string.pill_volume_on : R.string.pill_volume_off);
        b.pillVolume.setCompoundDrawableTintList(ContextCompat.getColorStateList(c,
                volume ? R.color.ws_safe : R.color.ws_warn));
        b.pillVolume.setOnClickListener(volume ? null
                : v -> startActivity(new Intent(c, SetupActivity.class)));
    }

    private void watchGuardians() {
        if (!Backend.isConfigured(requireContext())) return;
        String uid = Backend.uid(requireContext());
        if (uid == null) return;
        guardiansSub = CircleRepository.listenGuardians(requireContext(), uid, people -> {
            guardianCount = people.size();
            renderPills();
            renderCircleTile();
        });
    }

    // ------------------------------------------------------------------ SOS

    @Override
    public void onSosStatus(@NonNull SosState.Status s) {
        status = s;
        if (b == null) return;
        b.sosButton.setActive(s.active);
        if (s.active) {
            b.sosButton.setLabels(getString(R.string.sos_label_active), getString(R.string.sos_sub_active));
            b.sosHint.setText(R.string.sos_hint_active);
            b.sosLiveCard.setVisibility(View.VISIBLE);
            setStat(b.statViewers, s.viewers);
            setStat(b.statPhotos, s.photos);
            setStat(b.statClips, s.clips);
            setStat(b.statSms, s.smsSent);
            b.sosLiveNote.setText(s.cameraOn || s.micOn ? R.string.sos_live_note_media
                    : R.string.sos_live_note_no_media);
            main.removeCallbacks(timerTick);
            main.post(timerTick);
        } else {
            b.sosButton.setLabels(getString(R.string.sos_label), getString(R.string.sos_sub_hold));
            b.sosHint.setText(R.string.sos_hint);
            b.sosLiveCard.setVisibility(View.GONE);
            main.removeCallbacks(timerTick);
        }
    }

    private static void setStat(ViewStatBinding stat, int value) {
        stat.value.setText(String.valueOf(value));
    }

    private void renderTimer() {
        if (b == null || !status.active) return;
        long secs = Math.max(0, (System.currentTimeMillis() - status.startedAt) / 1000);
        b.sosTimer.setText(DateUtils.formatElapsedTime(secs));
    }

    /** Starts or stops SOS (after a completed hold or a confirmed dialog). */
    private void toggleSos() {
        if (status.active) {
            SosTrigger.stop(requireContext());
        } else {
            SosTrigger.start(requireContext(), SosTrigger.SOURCE_BUTTON);
        }
    }

    /** TalkBack path: confirm instead of hold. */
    private void confirmSos() {
        boolean active = status.active;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(active ? R.string.sos_confirm_stop_title : R.string.sos_confirm_title)
                .setMessage(active ? R.string.sos_confirm_stop_text : R.string.sos_confirm_text)
                .setPositiveButton(active ? R.string.sos_confirm_stop : R.string.sos_confirm_send,
                        (d, w) -> toggleSos())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
