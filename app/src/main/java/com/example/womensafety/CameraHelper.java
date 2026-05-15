package com.example.womensafety;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
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

public class CameraHelper {

    private static final String TAG = "CameraHelper";
    private Context context;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private File photoDirectory;
    private int photoCount = 0;
    private SurfaceTexture surfaceTexture;
    private Surface previewSurface;
    private boolean isCapturing = false;

    public CameraHelper(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        
        photoDirectory = new File(context.getExternalFilesDir(null), "SOSPhotos");
        if (!photoDirectory.exists()) {
            boolean created = photoDirectory.mkdirs();
            Log.d(TAG, "Photo directory created: " + created + " at " + photoDirectory.getAbsolutePath());
        }

        startBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        Log.d(TAG, "Background thread started");
    }

    public void capturePhoto(boolean useFrontCamera) {
        if (isCapturing) {
            Log.w(TAG, "Already capturing, skipping this request");
            return;
        }
        
        isCapturing = true;
        
        try {
            String cameraId = getFacingCameraId(useFrontCamera);
            if (cameraId == null) {
                Log.e(TAG, "❌ Camera ID not found for: " + (useFrontCamera ? "FRONT" : "BACK"));
                isCapturing = false;
                return;
            }

            Log.d(TAG, "📸 Opening " + (useFrontCamera ? "FRONT" : "BACK") + " camera (ID: " + cameraId + ")");
            openCamera(cameraId, useFrontCamera);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in capturePhoto", e);
            isCapturing = false;
        }
    }

    private String getFacingCameraId(boolean useFrontCamera) {
        try {
            String[] cameraList = cameraManager.getCameraIdList();
            Log.d(TAG, "Available cameras: " + cameraList.length);
            
            for (String cameraId : cameraList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                
                Log.d(TAG, "Camera " + cameraId + " facing: " + 
                      (facing == CameraCharacteristics.LENS_FACING_FRONT ? "FRONT" : 
                       facing == CameraCharacteristics.LENS_FACING_BACK ? "BACK" : "UNKNOWN"));
                
                if (facing != null) {
                    if (useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        return cameraId;
                    } else if (!useFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        return cameraId;
                    }
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accessing camera list", e);
        }
        return null;
    }

    private void openCamera(String cameraId, boolean isFrontCamera) {
        try {
            Log.d(TAG, "Attempting to open camera...");
            
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    Log.d(TAG, "✅ Camera opened successfully!");
                    createCaptureSession(isFrontCamera);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w(TAG, "⚠️ Camera disconnected");
                    camera.close();
                    cameraDevice = null;
                    isCapturing = false;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "❌ Camera error code: " + error);
                    camera.close();
                    cameraDevice = null;
                    isCapturing = false;
                }
            }, backgroundHandler);
            
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Camera permission denied!", e);
            isCapturing = false;
        } catch (CameraAccessException e) {
            Log.e(TAG, "❌ Cannot access camera", e);
            isCapturing = false;
        }
    }

    private void createCaptureSession(boolean isFrontCamera) {
        try {
            // FIXED: Higher resolution for better quality
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Log.d(TAG, "📸 Image available from reader");
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        saveImage(image, isFrontCamera);
                    } else {
                        Log.e(TAG, "❌ Image is null!");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error processing image", e);
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    closeCamera();
                }
            }, backgroundHandler);

            // FIXED: Larger preview surface for better sensor initialization
            surfaceTexture = new SurfaceTexture(0);
            surfaceTexture.setDefaultBufferSize(1280, 720);
            previewSurface = new Surface(surfaceTexture);

            Surface captureSurface = imageReader.getSurface();
            
            Log.d(TAG, "Creating capture session...");
            
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, captureSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            Log.d(TAG, "✅ Capture session configured");
                            
                            // FIXED: Start preview first, then capture after delay
                            startPreview(isFrontCamera);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "❌ Capture session configuration FAILED!");
                            closeCamera();
                        }
                    }, backgroundHandler);
                    
        } catch (CameraAccessException e) {
            Log.e(TAG, "❌ Error creating capture session", e);
            closeCamera();
        }
    }

    // FIXED: NEW METHOD - Start preview to initialize sensor
    private void startPreview(boolean isFrontCamera) {
        try {
            Log.d(TAG, "Starting preview to initialize camera sensor...");
            
            CaptureRequest.Builder previewBuilder = 
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            
            previewBuilder.addTarget(previewSurface);
            
            // Auto settings for preview
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            
            // Start repeating preview
            captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
            
            Log.d(TAG, "✅ Preview started, waiting for sensor to stabilize...");
            
            // FIXED: Wait longer (1.5 seconds) for sensor to fully initialize
            backgroundHandler.postDelayed(() -> {
                captureStillPicture(isFrontCamera);
            }, 1500);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "❌ Error starting preview", e);
            closeCamera();
        }
    }

    private void captureStillPicture(boolean isFrontCamera) {
        try {
            Log.d(TAG, "Setting up capture request...");
            
            // FIXED: Stop preview before capture
            captureSession.stopRepeating();
            
            CaptureRequest.Builder captureBuilder = 
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            
            captureBuilder.addTarget(imageReader.getSurface());
            
            // FIXED: Better camera settings
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            
            // FIXED: NO FLASH - Flash causes black images!
            // Auto-brightness adjustment instead
            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            
            // FIXED: Moderate exposure boost (was +2, now +1)
            captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1);
            
            // FIXED: Better JPEG quality
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 95);

            Log.d(TAG, "📸 Capturing picture NOW...");
            
            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                             @NonNull CaptureRequest request,
                                             @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "✅ Capture completed successfully!");
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "❌ Error capturing picture", e);
            closeCamera();
        }
    }

    private void saveImage(Image image, boolean isFrontCamera) {
        try {
            Log.d(TAG, "Processing image...");
            
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            Log.d(TAG, "Image size: " + bytes.length + " bytes");

            if (bytes.length < 1000) {
                Log.e(TAG, "❌ Image too small, likely corrupted!");
                return;
            }

            // Decode to bitmap to check if image is valid
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            
            if (bitmap == null) {
                Log.e(TAG, "❌ Failed to decode image to bitmap!");
                return;
            }
            
            Log.d(TAG, "✅ Bitmap decoded: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            
            // FIXED: Better rotation handling for front camera
            if (isFrontCamera) {
                Matrix matrix = new Matrix();
                matrix.postRotate(270); // Rotate
                matrix.postScale(-1, 1); // Mirror horizontally
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                Log.d(TAG, "Front camera image rotated and mirrored");
            } else {
                // FIXED: Also rotate back camera if needed
                Matrix matrix = new Matrix();
                matrix.postRotate(90); // Most phones need 90° rotation for back camera
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                Log.d(TAG, "Back camera image rotated");
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            String cameraType = isFrontCamera ? "front" : "back";
            File photoFile = new File(photoDirectory, "SOS_" + cameraType + "_" + timestamp + ".jpg");
            
            FileOutputStream output = new FileOutputStream(photoFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output);
            output.flush();
            output.close();
            bitmap.recycle();

            photoCount++;
            Log.d(TAG, "✅✅✅ Photo #" + photoCount + " saved successfully: " + photoFile.getName());
            Log.d(TAG, "📁 File path: " + photoFile.getAbsolutePath());
            Log.d(TAG, "📊 File size: " + (photoFile.length() / 1024) + " KB");
            
            // FIXED: Verify image is not black
            if (photoFile.length() < 10000) {
                Log.w(TAG, "⚠️ Warning: Image file seems very small, might be black!");
            } else {
                Log.d(TAG, "✅ Image size looks good!");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error saving image", e);
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        isCapturing = false;
        
        try {
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.close();
                captureSession = null;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error stopping capture session", e);
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
        
        Log.d(TAG, "Camera closed and resources released");
    }

    public void release() {
        closeCamera();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
                Log.d(TAG, "Background thread stopped");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }
}