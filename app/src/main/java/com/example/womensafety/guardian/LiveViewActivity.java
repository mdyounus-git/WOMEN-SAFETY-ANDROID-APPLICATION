package com.example.womensafety.guardian;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.womensafety.R;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Notifications;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivityLiveViewBinding;
import com.example.womensafety.live.Viewer;
import com.example.womensafety.map.MapsUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.RendererCommon;

import java.util.Locale;

/**
 * What Mom / Brother see: her live location on a map, and — only while her SOS is active —
 * live camera and microphone. When live video can't connect, the latest evidence photo is
 * shown instead, so there is always something to see.
 */
public class LiveViewActivity extends AppCompatActivity implements Viewer.Listener {

    private static final String EXTRA_WARD = "ward_uid";
    private static final String EXTRA_NAME = "ward_name";

    public static Intent intent(@NonNull Context c, @NonNull String wardUid, @NonNull String name) {
        return new Intent(c, LiveViewActivity.class)
                .putExtra(EXTRA_WARD, wardUid)
                .putExtra(EXTRA_NAME, name);
    }

    private ActivityLiveViewBinding b;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String wardUid;
    private String wardName;
    private String myUid;
    @Nullable
    private String wardPhone;

    private GoogleMap map;
    private Marker marker;
    private Circle accuracy;
    private LatLng lastLatLng;
    private long lastFixAt;
    private boolean firstFix = true;

    private boolean sosActive;
    /** Which of her cameras is live (reported by her phone). Back camera by default. */
    private boolean cameraFront;
    private boolean cameraSwitching;
    private String sosSession;
    @Nullable
    private Viewer viewer;
    private boolean rendererReady;
    private Viewer.State videoState = Viewer.State.CLOSED;

    private DatabaseReference locationRef;
    private DatabaseReference sosRef;
    private DatabaseReference userRef;
    private Query photosQuery;
    private ValueEventListener locationListener;
    private ValueEventListener sosListener;
    private ValueEventListener userListener;
    private ValueEventListener photosListener;

    private final Runnable refreshAge = new Runnable() {
        @Override
        public void run() {
            updateSubtitle();
            main.postDelayed(this, 15_000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        Ui.showOverLockScreen(this);
        b = ActivityLiveViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.header, true, false);
        Ui.padForSystemBars(b.panelContent, false, true);

        wardUid = getIntent().getStringExtra(EXTRA_WARD);
        wardName = getIntent().getStringExtra(EXTRA_NAME);
        if (wardUid == null) {
            finish();
            return;
        }
        if (TextUtils.isEmpty(wardName)) wardName = getString(R.string.someone_in_circle);
        Notifications.cancel(this, Notifications.alertIdFor(wardUid));

        b.name.setText(wardName);
        b.privacyText.setText(getString(R.string.live_privacy_text, wardName));
        b.back.setOnClickListener(v -> finish());
        b.call.setOnClickListener(v -> call());
        b.directions.setOnClickListener(v -> directions());
        b.camFront.setOnClickListener(v -> requestCamera(true));
        b.camBack.setOnClickListener(v -> requestCamera(false));
        renderCamera();
        b.mute.setOnClickListener(v -> toggleMute());
        b.statusPill.setOnClickListener(v -> {
            if (viewer != null && videoState == Viewer.State.FAILED) viewer.requestStream();
        });
        setupMap();
        showSafeState();

        Backend.ensureSignedIn(this, new Backend.UidCallback() {
            @Override
            public void onReady(@NonNull String uid) {
                myUid = uid;
                listen();
            }

            @Override
            public void onError(@NonNull Exception e) {
                Toast.makeText(LiveViewActivity.this, R.string.error_offline, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupMap() {
        if (!MapsUtil.isKeyConfigured(this)) {
            b.mapMissing.setVisibility(View.VISIBLE);
            return;
        }
        SupportMapFragment f = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (f == null) {
            f = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().replace(R.id.map, f).commitNow();
        }
        f.getMapAsync(m -> {
            map = m;
            MapsUtil.applyStyle(this, m, false);
            if (lastLatLng != null) showOnMap(lastLatLng, -1);
        });
    }

    // ------------------------------------------------------------------ data

    private void listen() {
        locationRef = Backend.location(wardUid);
        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Double lat = s.child("lat").getValue(Double.class);
                Double lng = s.child("lng").getValue(Double.class);
                if (lat == null || lng == null) return;
                Double acc = s.child("acc").getValue(Double.class);
                Double speed = s.child("speed").getValue(Double.class);
                Long ts = s.child("ts").getValue(Long.class);
                Long battery = s.child("battery").getValue(Long.class);
                onLocation(new LatLng(lat, lng), acc == null ? -1 : acc, speed,
                        ts == null ? 0 : ts, battery == null ? -1 : battery.intValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onAccessLost();
            }
        };
        locationRef.addValueEventListener(locationListener);

        sosRef = Backend.sos(wardUid);
        sosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Boolean active = s.child("active").getValue(Boolean.class);
                String cam = s.child("camera").getValue(String.class);
                if (cam != null) {
                    cameraFront = "front".equals(cam);
                    cameraSwitching = false;
                    renderCamera();
                }
                onSos(Boolean.TRUE.equals(active), s.child("sessionId").getValue(String.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onAccessLost();
            }
        };
        sosRef.addValueEventListener(sosListener);

        userRef = Backend.user(wardUid);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                wardPhone = s.child("phone").getValue(String.class);
                b.call.setEnabled(!TextUtils.isEmpty(wardPhone));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        userRef.addValueEventListener(userListener);
        main.post(refreshAge);
    }

    private void onLocation(LatLng pos, double acc, @Nullable Double speed, long ts, int battery) {
        lastLatLng = pos;
        lastFixAt = ts;
        updateSubtitle();
        if (battery >= 0) {
            b.battery.setVisibility(View.VISIBLE);
            b.battery.setText(getString(R.string.percent, battery));
        }
        String detail = acc > 0 ? getString(R.string.live_accuracy, Math.round(acc)) : "";
        if (speed != null && speed > 1) {
            detail += (detail.isEmpty() ? "" : " · ")
                    + getString(R.string.live_speed, Math.round(speed * 3.6));
        }
        b.placeDetail.setText(detail);
        MapsUtil.describePlace(this, pos.latitude, pos.longitude, place -> {
            if (b == null) return;
            String name = place == null ? "" : place.shortName();
            b.place.setText(name.isEmpty()
                    ? String.format(Locale.US, "%.5f, %.5f", pos.latitude, pos.longitude) : name);
        });
        showOnMap(pos, acc);
    }

    private void showOnMap(LatLng pos, double acc) {
        if (map == null) return;
        int color = ContextCompat.getColor(this, sosActive ? R.color.ws_sos : R.color.ws_plum);
        if (marker == null) {
            marker = map.addMarker(new MarkerOptions().position(pos).title(wardName));
        } else {
            marker.setPosition(pos);
        }
        if (acc > 0) {
            if (accuracy == null) {
                accuracy = map.addCircle(new CircleOptions().center(pos).radius(acc)
                        .strokeWidth(2f).strokeColor(color).fillColor((color & 0x00FFFFFF) | 0x22000000));
            } else {
                accuracy.setCenter(pos);
                accuracy.setRadius(acc);
                accuracy.setStrokeColor(color);
                accuracy.setFillColor((color & 0x00FFFFFF) | 0x22000000);
            }
        }
        if (firstFix) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
            firstFix = false;
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLng(pos));
        }
    }

    private void onSos(boolean active, @Nullable String session) {
        boolean changed = active != sosActive || (session != null && !session.equals(sosSession));
        sosActive = active;
        sosSession = session;
        if (!changed) return;
        if (active) showSosState();
        else showSafeState();
        if (lastLatLng != null) showOnMap(lastLatLng, accuracy == null ? -1 : accuracy.getRadius());
    }

    private void onAccessLost() {
        Toast.makeText(this, getString(R.string.live_access_lost, wardName), Toast.LENGTH_LONG).show();
        if (myUid != null) Backend.watching(myUid).child(wardUid).removeValue();
        finish();
    }

    // ------------------------------------------------------------------ states

    private void showSafeState() {
        stopVideo();
        b.recPill.setVisibility(View.GONE);
        b.privacyPanel.setVisibility(View.VISIBLE);
        b.snapshot.setVisibility(View.GONE);
        b.video.setVisibility(View.GONE);
        b.videoControls.setVisibility(View.GONE);
        b.statusPill.setVisibility(View.GONE);
        b.stateChip.setText(R.string.live_state_safe);
        b.stateChip.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.ws_safe_container));
        b.stateChip.setCompoundDrawableTintList(ContextCompat.getColorStateList(this, R.color.ws_safe));
        updateSubtitle();
    }

    private void showSosState() {
        b.privacyPanel.setVisibility(View.GONE);
        b.stateChip.setText(R.string.live_state_sos);
        b.stateChip.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.ws_sos_container));
        b.stateChip.setCompoundDrawableTintList(ContextCompat.getColorStateList(this, R.color.ws_sos));
        b.statusPill.setVisibility(View.VISIBLE);
        listenLatestPhoto();
        startVideo();
        updateSubtitle();
    }

    private void startVideo() {
        if (viewer != null || myUid == null) return;
        viewer = new Viewer(this, wardUid, myUid, wardName, this);
        if (!rendererReady) {
            b.video.init(viewer.eglContext(), null);
            b.video.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            b.video.setEnableHardwareScaler(true);
            rendererReady = true;
        }
        b.video.setVisibility(View.VISIBLE);
        b.videoControls.setVisibility(View.VISIBLE);
        viewer.start(b.video);
    }

    private void stopVideo() {
        if (viewer != null) {
            viewer.stop();
            viewer = null;
        }
        if (rendererReady) {
            b.video.release();
            rendererReady = false;
        }
        if (photosQuery != null && photosListener != null) photosQuery.removeEventListener(photosListener);
        photosQuery = null;
    }

    @Override
    public void onStateChanged(@NonNull Viewer.State state) {
        videoState = state;
        if (b == null || !sosActive) return;
        switch (state) {
            case CONNECTING:
                setPill(R.string.live_pill_connecting, R.color.ws_warn);
                break;
            case LIVE:
                setPill(R.string.live_pill_live, R.color.ws_sos);
                b.snapshot.setVisibility(View.GONE);
                b.recPill.setVisibility(View.VISIBLE);
                break;
            case RECONNECTING:
                setPill(R.string.live_pill_reconnecting, R.color.ws_warn);
                break;
            case FAILED:
                setPill(R.string.live_pill_failed, R.color.ws_ink_faint);
                b.snapshot.setVisibility(b.snapshot.getDrawable() == null ? View.GONE : View.VISIBLE);
                break;
            default:
                break;
        }
    }

    private void setPill(int text, int dotColor) {
        b.statusPill.setText(text);
        b.statusPill.setCompoundDrawableTintList(ContextCompat.getColorStateList(this, dotColor));
    }

    /** Latest evidence photo: shown whenever live video isn't connected. */
    private void listenLatestPhoto() {
        if (sosSession == null || photosQuery != null) return;
        photosQuery = Backend.evidence(wardUid, sosSession).child("photos").limitToLast(1);
        photosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot p : snapshot.getChildren()) {
                    String jpg = p.child("jpg").getValue(String.class);
                    if (jpg == null) continue;
                    try {
                        byte[] bytes = Base64.decode(jpg, Base64.DEFAULT);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            b.snapshot.setImageBitmap(bmp);
                            if (videoState != Viewer.State.LIVE) b.snapshot.setVisibility(View.VISIBLE);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        photosQuery.addValueEventListener(photosListener);
    }

    private void updateSubtitle() {
        if (b == null) return;
        String age = lastFixAt > 0 ? Ui.relativeTime(this, lastFixAt) : getString(R.string.time_unknown);
        b.subtitle.setText(getString(sosActive ? R.string.live_subtitle_sos : R.string.live_subtitle_safe, age));
    }

    // ------------------------------------------------------------------ actions

    /** Asks her phone to use the front or back camera (no-op if already on it). */
    private void requestCamera(boolean front) {
        if (viewer == null || cameraSwitching || front == cameraFront) return;
        cameraSwitching = true;
        viewer.switchCamera();
        Toast.makeText(this, getString(R.string.live_cam_switching,
                getString(front ? R.string.live_cam_front : R.string.live_cam_back).toLowerCase()),
                Toast.LENGTH_SHORT).show();
        // Her phone reports back which camera is live; give up waiting after a few seconds.
        main.postDelayed(() -> {
            cameraSwitching = false;
            renderCamera();
        }, 5_000L);
        renderCamera();
    }

    private void renderCamera() {
        if (b == null) return;
        styleCameraOption(b.camFront, cameraFront);
        styleCameraOption(b.camBack, !cameraFront);
        b.cameraSwitch.setAlpha(cameraSwitching ? 0.6f : 1f);
    }

    private void styleCameraOption(android.widget.TextView option, boolean selected) {
        int fg = selected ? 0xFF1F1524 : 0xFFFFFFFF;
        option.setTextColor(fg);
        option.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(fg));
        option.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                selected ? 0xFFFFFFFF : 0x00000000));
        option.setSelected(selected);
    }

    private void toggleMute() {
        if (viewer == null) return;
        boolean mute = !viewer.isMuted();
        viewer.setMuted(mute);
        b.mute.setIconResource(mute ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
    }

    private void call() {
        if (TextUtils.isEmpty(wardPhone)) return;
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + wardPhone)));
    }

    private void directions() {
        if (lastLatLng == null) {
            Toast.makeText(this, R.string.live_no_location, Toast.LENGTH_SHORT).show();
            return;
        }
        String q = String.format(Locale.US, "%.6f,%.6f", lastLatLng.latitude, lastLatLng.longitude);
        Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + q));
        try {
            startActivity(nav);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + q)));
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (locationRef != null && locationListener != null) locationRef.removeEventListener(locationListener);
        if (sosRef != null && sosListener != null) sosRef.removeEventListener(sosListener);
        if (userRef != null && userListener != null) userRef.removeEventListener(userListener);
        stopVideo();
        super.onDestroy();
        b = null;
    }
}
