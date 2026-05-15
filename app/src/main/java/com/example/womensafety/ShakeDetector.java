package com.example.womensafety;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {

    private static final float SHAKE_THRESHOLD = 15.0f;
    private static final int SHAKE_WAIT_TIME_MS = 500;
    private static final int SHAKE_COUNT_THRESHOLD = 3;

    private OnShakeListener listener;
    private long lastShakeTime = 0;
    private int shakeCount = 0;

    public interface OnShakeListener {
        void onShake();
    }

    public void setOnShakeListener(OnShakeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

            if (acceleration > SHAKE_THRESHOLD) {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastShakeTime < SHAKE_WAIT_TIME_MS) {
                    shakeCount++;
                    if (shakeCount >= SHAKE_COUNT_THRESHOLD && listener != null) {
                        listener.onShake();
                        shakeCount = 0;
                    }
                } else {
                    shakeCount = 1;
                }

                lastShakeTime = currentTime;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }
}