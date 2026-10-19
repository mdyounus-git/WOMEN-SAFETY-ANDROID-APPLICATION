package com.example.womensafety.evidence;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.womensafety.R;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivityEvidenceBinding;
import com.example.womensafety.databinding.ItemEvidenceAudioBinding;
import com.example.womensafety.databinding.ItemEvidenceSessionBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Photos and audio saved during each SOS, kept privately on this phone. */
public class EvidenceActivity extends AppCompatActivity {

    private static final String AUTHORITY = "com.example.womensafety.provider";

    private ActivityEvidenceBinding b;
    private final ExecutorService decoder = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    @Nullable
    private MediaPlayer player;
    @Nullable
    private MaterialButton playingButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        b = ActivityEvidenceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.root, true, true);
        b.back.setOnClickListener(v -> finish());
        b.list.setLayoutManager(new LinearLayoutManager(this));
        load();
    }

    private void load() {
        List<EvidenceStore.Session> sessions = EvidenceStore.listSessions(this);
        b.empty.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        b.list.setAdapter(new SessionAdapter(sessions));
    }

    @Override
    protected void onStop() {
        stopPlayback();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ sessions

    private final class SessionAdapter extends RecyclerView.Adapter<SessionHolder> {
        private final List<EvidenceStore.Session> sessions;

        SessionAdapter(List<EvidenceStore.Session> sessions) {
            this.sessions = sessions;
        }

        @NonNull
        @Override
        public SessionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SessionHolder(ItemEvidenceSessionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SessionHolder h, int position) {
            EvidenceStore.Session s = sessions.get(position);
            h.b.date.setText(DateUtils.formatDateTime(EvidenceActivity.this, s.startedAt,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH));
            h.b.counts.setText(getString(R.string.evidence_counts,
                    getResources().getQuantityString(R.plurals.photos, s.photos.size(), s.photos.size()),
                    getResources().getQuantityString(R.plurals.clips, s.audio.size(), s.audio.size())));
            h.b.photos.setVisibility(s.photos.isEmpty() ? View.GONE : View.VISIBLE);
            h.b.photos.setLayoutManager(new LinearLayoutManager(EvidenceActivity.this,
                    LinearLayoutManager.HORIZONTAL, false));
            h.b.photos.setAdapter(new PhotoAdapter(s.photos));

            h.b.audioList.removeAllViews();
            for (File f : s.audio) {
                ItemEvidenceAudioBinding row = ItemEvidenceAudioBinding.inflate(getLayoutInflater(),
                        h.b.audioList, false);
                row.label.setText(getString(R.string.evidence_audio_label,
                        DateUtils.formatDateTime(EvidenceActivity.this, f.lastModified(), DateUtils.FORMAT_SHOW_TIME)));
                row.play.setOnClickListener(v -> togglePlay(f, row.play));
                row.getRoot().setOnClickListener(v -> togglePlay(f, row.play));
                h.b.audioList.addView(row.getRoot());
            }
            h.b.share.setOnClickListener(v -> share(s));
            h.b.delete.setOnClickListener(v -> confirmDelete(s));
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }
    }

    private static final class SessionHolder extends RecyclerView.ViewHolder {
        final ItemEvidenceSessionBinding b;

        SessionHolder(ItemEvidenceSessionBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    // ------------------------------------------------------------------ photos

    private final class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private final List<File> files;

        PhotoAdapter(List<File> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_evidence_photo, parent, false);
            return new PhotoHolder((ImageView) v);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder h, int position) {
            File f = files.get(position);
            h.image.setTag(f.getPath());
            h.image.setOnClickListener(v -> view(f, "image/jpeg"));
            Bitmap cached = thumbs.get(f.getPath());
            if (cached != null) {
                h.image.setImageBitmap(cached);
                return;
            }
            h.image.setImageDrawable(null);
            decoder.execute(() -> {
                Bitmap bmp = decodeThumb(f, Ui.dp(EvidenceActivity.this, 104));
                if (bmp == null) return;
                thumbs.put(f.getPath(), bmp);
                main.post(() -> {
                    if (f.getPath().equals(h.image.getTag())) h.image.setImageBitmap(bmp);
                });
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }
    }

    private static final class PhotoHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        PhotoHolder(ImageView image) {
            super(image);
            this.image = image;
        }
    }

    @Nullable
    private static Bitmap decodeThumb(File f, int targetPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= targetPx) sample *= 2;
        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), real);
    }

    // ------------------------------------------------------------------ audio

    private void togglePlay(File f, MaterialButton button) {
        boolean wasThis = button == playingButton;
        stopPlayback();
        if (wasThis) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(f.getPath());
            mp.setOnCompletionListener(x -> stopPlayback());
            mp.prepare();
            mp.start();
            player = mp;
            playingButton = button;
            button.setIconResource(R.drawable.ic_pause);
        } catch (Exception e) {
            Toast.makeText(this, R.string.evidence_play_failed, Toast.LENGTH_SHORT).show();
            stopPlayback();
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
        }
        player = null;
        if (playingButton != null) playingButton.setIconResource(R.drawable.ic_play);
        playingButton = null;
    }

    // ------------------------------------------------------------------ actions

    private Uri uriFor(File f) {
        return FileProvider.getUriForFile(this, AUTHORITY, f);
    }

    private void view(File f, String mime) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriFor(f), mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.evidence_no_viewer, Toast.LENGTH_SHORT).show();
        }
    }

    private void share(EvidenceStore.Session s) {
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            for (File f : s.photos) uris.add(uriFor(f));
            for (File f : s.audio) uris.add(uriFor(f));
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        if (uris.isEmpty()) return;
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.evidence_share)));
    }

    private void confirmDelete(EvidenceStore.Session s) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.evidence_delete_title)
                .setMessage(R.string.evidence_delete_text)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    stopPlayback();
                    EvidenceStore.deleteSession(this, s);
                    load();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
