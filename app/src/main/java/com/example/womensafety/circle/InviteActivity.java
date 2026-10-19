package com.example.womensafety.circle;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.womensafety.R;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivityInviteBinding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows a one-time QR code (valid 10 minutes) that family members scan to join her circle.
 * The code is deleted as soon as she leaves this screen.
 */
public class InviteActivity extends AppCompatActivity {

    private static final String TAG = "Invite";

    private ActivityInviteBinding b;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable
    private CircleRepository.Invite invite;
    @Nullable
    private CircleRepository.Subscription guardiansSub;
    private final Set<String> knownGuardians = new HashSet<>();
    private boolean guardiansLoaded;

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            if (invite == null || b == null) return;
            long left = invite.expiresAt - System.currentTimeMillis();
            if (left <= 0) {
                showOverlay(getString(R.string.invite_expired));
                b.expires.setText(R.string.invite_expired);
                return;
            }
            b.expires.setText(getString(R.string.invite_expires_in, DateUtils.formatElapsedTime(left / 1000)));
            main.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        b = ActivityInviteBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.column, true, true);
        b.back.setOnClickListener(v -> finish());
        b.regenerate.setOnClickListener(v -> createInvite());
        b.share.setOnClickListener(v -> shareCode());
        createInvite();
        watchForJoins();
    }

    private void createInvite() {
        if (invite != null) CircleRepository.cancelInvite(invite.code);
        invite = null;
        main.removeCallbacks(countdown);
        b.overlay.setVisibility(View.GONE);
        b.loading.setVisibility(View.VISIBLE);
        b.qr.setImageDrawable(null);
        b.code.setText("");
        b.share.setEnabled(false);
        CircleRepository.createInvite(this, new CircleRepository.Result<CircleRepository.Invite>() {
            @Override
            public void onSuccess(CircleRepository.Invite value) {
                if (b == null) {
                    CircleRepository.cancelInvite(value.code);
                    return;
                }
                invite = value;
                b.loading.setVisibility(View.GONE);
                b.share.setEnabled(true);
                b.code.setText(pretty(value.code));
                try {
                    int size = Ui.dp(InviteActivity.this, 232);
                    Bitmap qr = QrRenderer.render(value.qrPayload(), size,
                            ContextCompat.getColor(InviteActivity.this, R.color.ws_plum_deep_fixed),
                            0xFFFFFFFF);
                    b.qr.setImageBitmap(qr);
                } catch (Exception e) {
                    Log.e(TAG, "QR render failed", e);
                }
                main.post(countdown);
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (b == null) return;
                b.loading.setVisibility(View.GONE);
                showOverlay(getString(R.string.invite_failed));
            }
        });
    }

    private void watchForJoins() {
        String uid = Backend.uid(this);
        if (uid == null) {
            // Signed in by createInvite(); try again shortly.
            main.postDelayed(this::watchForJoins, 1_000L);
            return;
        }
        guardiansSub = CircleRepository.listenGuardians(this, uid, this::onGuardians);
    }

    private void onGuardians(List<Person> people) {
        if (b == null) return;
        for (Person p : people) {
            if (knownGuardians.add(p.uid) && guardiansLoaded) {
                b.joinedCard.setVisibility(View.VISIBLE);
                b.joinedText.setText(getString(R.string.invite_joined, p.name));
                b.joinedCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
        guardiansLoaded = true;
    }

    private void showOverlay(String text) {
        b.overlay.setVisibility(View.VISIBLE);
        b.overlayText.setText(text);
        b.share.setEnabled(false);
    }

    private void shareCode() {
        if (invite == null) return;
        String text = getString(R.string.invite_share_text, pretty(invite.code), invite.qrPayload());
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text), getString(R.string.invite_share_code)));
    }

    /** "ABCDEFGHJK" -> "ABCD·EFGH·JK" for reading aloud. */
    private static String pretty(String code) {
        return code.substring(0, 4) + "·" + code.substring(4, 8) + "·" + code.substring(8);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (guardiansSub != null) guardiansSub.remove();
        if (invite != null) CircleRepository.cancelInvite(invite.code);
        b = null;
        super.onDestroy();
    }
}
