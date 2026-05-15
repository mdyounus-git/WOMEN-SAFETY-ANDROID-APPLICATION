package com.example.womensafety;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class SimpleCameraHelper {

    private static final String TAG = "SimpleCameraHelper";
    private Context context;
    private CameraManager cameraManager;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private File photoDirectory;

    public interface PhotoCallback {
        void onPhotoTaken(String filePath);
        void onError(String error);
    }

    public SimpleCameraHelper(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        
        photoDirectory = new File(context.getExternalFilesDir(null), "SOSPhotos");
        if (!photoDirectory.exists()) {
            photoDirectory.mkdirs();
            Log.d(TAG, "📁 Photo directory created: " + photoDirectory.getAbsolutePath());
        }

        startBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @SuppressLint("MissingPermission")
    public void takePhoto(boolean useFrontCamera, PhotoCallback callback) {
        try {
            String cameraId = getCameraId(useFrontCamera);
            if (cameraId == null) {
                callback.onError("Camera not found");
                return;
            }

            Log.d(TAG, "Opening " + (useFrontCamera ? "FRONT" : "BACK") + " camera...");

            ImageReader imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        String filePath = saveImage(image, useFrontCamera);
                        if (filePath != null) {
                            callback.onPhotoTaken(filePath);
                        } else {
                            callback.onError("Failed to save image");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing image", e);
                    callback.onError(e.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    reader.close();
                }
            }, backgroundHandler);

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "✅ Camera opened");
                    captureImage(camera, imageReader, callback);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    callback.onError("Camera disconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    callback.onError("Camera error: " + error);
                }
            }, backgroundHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            callback.onError(e.getMessage());
        }
    }

    private void captureImage(CameraDevice camera, ImageReader imageReader, PhotoCallback callback) {
        try {
            // Create dummy surface for preview
            SurfaceTexture surfaceTexture = new SurfaceTexture(0);
            surfaceTexture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(surfaceTexture);

            camera.createCaptureSession(
                Arrays.asList(previewSurface, imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            // Start preview first
                            CaptureRequest.Builder previewBuilder = 
                                camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            previewBuilder.addTarget(previewSurface);
                            previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                            
                            session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);

                            // Wait 800ms for sensor to initialize, then capture
                            backgroundHandler.postDelayed(() -> {
                                try {
                                    session.stopRepeating();
                                    
                                    CaptureRequest.Builder captureBuilder = 
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                    captureBuilder.addTarget(imageReader.getSurface());
                                    captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                    captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 90);
                                    
                                    session.capture(captureBuilder.build(), null, backgroundHandler);
                                    
                                    // Clean up after 1 second
                                    backgroundHandler.postDelayed(() -> {
                                        session.close();
                                        camera.close();
                                        previewSurface.release();
                                        surfaceTexture.release();
                                    }, 1000);
                                    
                                } catch (CameraAccessException e) {
                                    Log.e(TAG, "Capture error", e);
                                    callback.onError("Capture failed");
                                }
                            }, 800);

                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Preview error", e);
                            callback.onError("Preview failed");
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        callback.onError("Session config failed");
                    }
                }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Session creation error", e);
            callback.onError(e.getMessage());
        }
    }

    private String getCameraId(boolean useFrontCamera) {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                
                if (facing != null) {
                    if (useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        return id;
                    } else if (!useFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        return id;
                    }
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting camera ID", e);
        }
        return null;
    }

    private String saveImage(Image image, boolean isFrontCamera) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String cameraType = isFrontCamera ? "front" : "back";
            File photoFile = new File(photoDirectory, "SOS_" + cameraType + "_" + timestamp + ".jpg");

            FileOutputStream output = new FileOutputStream(photoFile);
            output.write(bytes);
            output.close();

            Log.d(TAG, "✅ Photo saved: " + photoFile.getName() + " (" + (bytes.length / 1024) + " KB)");
            return photoFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    public void release() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping thread", e);
            }
        }
    }
}