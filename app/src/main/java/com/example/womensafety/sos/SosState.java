package com.example.womensafety.sos;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArraySet;

/** Live SOS status for the UI (the service is the single writer). */
public final class SosState {

    /** Snapshot of what the SOS service is doing right now. */
    public static final class Status {
        public final boolean active;
        public final long startedAt;
        public final int smsSent;
        public final int viewers;
        public final int photos;
        public final int clips;
        public final boolean cameraOn;
        public final boolean micOn;

        Status(boolean active, long startedAt, int smsSent, int viewers, int photos, int clips,
               boolean cameraOn, boolean micOn) {
            this.active = active;
            this.startedAt = startedAt;
            this.smsSent = smsSent;
            this.viewers = viewers;
            this.photos = photos;
            this.clips = clips;
            this.cameraOn = cameraOn;
            this.micOn = micOn;
        }
    }

    public interface Observer {
        void onSosStatus(@NonNull Status status);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Observer> OBSERVERS = new CopyOnWriteArraySet<>();
    private static volatile Status current = new Status(false, 0, 0, 0, 0, 0, false, false);

    private SosState() {
    }

    @NonNull
    public static Status current() {
        return current;
    }

    static void publish(@NonNull Status status) {
        current = status;
        MAIN.post(() -> {
            for (Observer o : OBSERVERS) o.onSosStatus(status);
        });
    }

    static void publishInactive() {
        publish(new Status(false, 0, 0, 0, 0, 0, false, false));
    }

    /** Registers and immediately delivers the current status. */
    @MainThread
    public static void observe(@NonNull Observer observer) {
        OBSERVERS.add(observer);
        observer.onSosStatus(current);
    }

    @MainThread
    public static void remove(@NonNull Observer observer) {
        OBSERVERS.remove(observer);
    }
}
