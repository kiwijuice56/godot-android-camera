package org.godotengine.plugin.android.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.camera2.CaptureRequest;
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
    private int REQUEST_CODE_PERMISSIONS = 1001;

    private Executor executor = Executors.newSingleThreadExecutor();
    ProcessCameraProvider cameraProvider;

    private Activity activity;
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
    public void startCamera() {
        if (isRunning) {
            Log.e(getPluginName(), "Camera is already running.");
        }
        isRunning = true;

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

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
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
                new Range<>(60, 60)
        );

        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setResolutionStrategy(
                        new ResolutionStrategy(
                                new Size(128, 128),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build();

        ImageAnalysis imageAnalysis = imageAnalysisBuilder
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        imageAnalysis.setAnalyzer(executor, image -> {
            long timestamp = System.currentTimeMillis();
            if (image.getFormat() == PixelFormat.RGBA_8888) {
                ByteBuffer buffer = image.getImage().getPlanes()[0].getBuffer();
                byte[] rgbData = new byte[buffer.remaining()];
                buffer.get(rgbData);
                emitSignal("on_camera_frame", timestamp, rgbData, image.getWidth(), image.getHeight());
            }
            image.close();
        });
        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector, imageAnalysis);
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