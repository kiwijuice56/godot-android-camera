// For transparency: Much of this code was generated with the help of ChatGPT.

// I reviewed and refactored it, but there may be underlying issues due to my inexperience with Android code.

// This code also makes use of the deprecated Camera API, so it is not guaranteed to work in the future.

package org.godotengine.plugin.android.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

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
                new Range<>(60, 120)
        );

        ImageAnalysis imageAnalysis = imageAnalysisBuilder
                .setTargetResolution(new Size(256, 256))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                .build();
        imageAnalysis.setAnalyzer(executor, image -> {
            Image data = image.getImage();
            if (data != null) {
                data.
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

    // Modified from https://github.com/yushulx/NV21-to-RGB
    // Converts an image from YUV to RGB_888 as a byte array
    private static byte[] yuv2rgb(byte[] yuv, int width, int height) {
        int total = width * height;
        byte[] rgb = new byte[total * 3];
        int Y, Cb = 0, Cr = 0, index = 0;
        int R, G, B;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Y = yuv[y * width + x];
                if (Y < 0) Y += 255;

                if ((x & 1) == 0) {
                    Cr = yuv[(y >> 1) * (width) + x + total];
                    Cb = yuv[(y >> 1) * (width) + x + total + 1];

                    if (Cb < 0) Cb += 127; else Cb -= 128;
                    if (Cr < 0) Cr += 127; else Cr -= 128;
                }

                R = Y + Cr + (Cr >> 2) + (Cr >> 3) + (Cr >> 5);
                G = Y - (Cb >> 2) + (Cb >> 4) + (Cb >> 5) - (Cr >> 1) + (Cr >> 3) + (Cr >> 4) + (Cr >> 5);
                B = Y + Cb + (Cb >> 1) + (Cb >> 2) + (Cb >> 6);

                if (R < 0) R = 0; else if (R > 255) R = 255;
                if (G < 0) G = 0; else if (G > 255) G = 255;
                if (B < 0) B = 0; else if (B > 255) B = 255;

                rgb[index++] = (byte) R;
                rgb[index++] = (byte) G;
                rgb[index++] = (byte) B;
            }
        }

        return rgb;
    }
}