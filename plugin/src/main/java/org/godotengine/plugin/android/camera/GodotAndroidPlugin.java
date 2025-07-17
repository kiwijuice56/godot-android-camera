package org.godotengine.plugin.android.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** @noinspection ALL*/
public class GodotAndroidPlugin extends GodotPlugin {
    // Recording parameters
    private int width;
    private int height;
    private boolean flash_on;

    private int REQUEST_CODE_PERMISSIONS = 1001;

    private Executor executor = Executors.newSingleThreadExecutor();
    ProcessCameraProvider cameraProvider;

    private Activity activity;
    private Camera camera;
    private volatile boolean isRunning = false;

    public GodotAndroidPlugin(Godot godot) {
        super(godot);
        activity = godot.getActivity();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotAndroidCamera";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        return Collections.singleton(new SignalInfo("on_camera_frame", Long.class, byte[].class, Integer.class, Integer.class));
    }

    @UsedByGodot
    public void startCamera(int recordingWidth, int recordingHeight, boolean flash_on) {
        if (isRunning) {
            Log.e(getPluginName(), "Camera is already running.");
            return;
        }
        isRunning = true;

        this.width = recordingWidth;
        this.height = recordingHeight;
        this.flash_on = flash_on;

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);

                } catch (ExecutionException | InterruptedException e) {
                    // This should never be reached
                }
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    @UsedByGodot
    public void stopCamera() {
        if (!isRunning) {
            Log.e(getPluginName(), "Camera is already stopped.");
        }
        isRunning = false;

        new Handler(Looper.getMainLooper()).post(() -> {
            if (cameraProvider != null) {
                if (flash_on && camera != null) {
                    camera.getCameraControl().enableTorch(false);
                }
                cameraProvider.unbindAll();
            }
        });
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        // Find the highest FPS range before creating our ImageAnalysis
        Range<Integer> highestFpsRange = null;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    Range<Integer>[] availableFpsRanges = characteristics.get(
                            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

                    if (availableFpsRanges != null) {
                        for (Range<Integer> range : availableFpsRanges) {
                            if (highestFpsRange == null ||
                                    range.getUpper() > highestFpsRange.getUpper() ||
                                    (range.getUpper().equals(highestFpsRange.getUpper()) && range.getLower() > highestFpsRange.getLower())) {
                                highestFpsRange = range;
                            }
                        }
                    }
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        Log.w(getPluginName(), String.format("Starting camera with FPS range: %s", String.valueOf(highestFpsRange)));

        Preview preview = new Preview.Builder()
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        ImageAnalysis.Builder imageAnalysisBuilder = new ImageAnalysis.Builder();

        Camera2Interop.Extender<ImageAnalysis> ext =
                new Camera2Interop.Extender<>(imageAnalysisBuilder);
        ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                highestFpsRange
        );

        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(
                        new ResolutionStrategy(
                                new Size(width, height),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build();

        ImageAnalysis imageAnalysis = imageAnalysisBuilder
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        imageAnalysis.setAnalyzer(executor, image -> {
            try {
                if (image.getFormat() == PixelFormat.RGBA_8888) {
                    ByteBuffer buffer = image.getImage().getPlanes()[0].getBuffer();
                    byte[] rgbData = new byte[buffer.remaining()];
                    buffer.get(rgbData);
                    long timestamp = System.currentTimeMillis();

                    // Prevent blocking the main thread while sending camera data to Godot
                    new Thread(() -> {
                        emitSignal("on_camera_frame", timestamp, rgbData, image.getWidth(), image.getHeight());
                    }).start();
                }
            } catch (Exception e) {
                Log.e("ImageAnalyzer", "Analyzer exception", e);
            } finally {
                image.close();
            }
        });
        cameraProvider.unbindAll();

        camera = cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector, imageAnalysis);

        if (flash_on) {
            camera.getCameraControl().enableTorch(true);
        }
    }

    @UsedByGodot
    public boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @UsedByGodot
    public void requestCameraPermissions() {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSIONS);
    }
}