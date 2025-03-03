// For transparency: Much of this code was generated with the help of ChatGPT.

// I reviewed and refactored it, but there may be underlying issues due to my inexperience with Android code.

// This code also makes use of the deprecated Camera API, so it is not guaranteed to work in the future.

package org.godotengine.plugin.android.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** @noinspection ALL*/
public class GodotAndroidPlugin extends GodotPlugin {
    private static final int CAMERA_PERMISSION_REQUEST = 1024;

    private Activity activity;
    private Camera camera;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private SurfaceTexture surfaceTexture;

    // in milliseconds (ex: capture_interval = 50 ms will record at 20 fps)
    protected int capture_interval;

    private byte[] previewBuffer;
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
        return Collections.singleton(new SignalInfo("on_camera_frame", byte[].class, Integer.class, Integer.class));
    }

    @UsedByGodot
    public void startCamera(int desired_width, int desired_height, int capture_interval, boolean flash_on) {
        if (isRunning) {
            Log.w(getPluginName(), "Camera already started.");
            return;
        }
        isRunning = true;

        this.capture_interval = capture_interval;

        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        cameraHandler.post(() -> {
            try {
                camera = Camera.open();
                Camera.Parameters params = camera.getParameters();

                List<String> focusModes = params.getSupportedFocusModes();
                if (focusModes != null) {
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        Log.i(getPluginName(), "Set focus mode to CONTINUOUS_PICTURE");
                    } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        Log.i(getPluginName(), "Set focus mode to AUTO");
                    } else {
                        Log.w(getPluginName(), "No supported autofocus modes found.");
                    }
                }

                Camera.Size size = getClosestPreviewSize(params, desired_width, desired_height);
                params.setPreviewSize(size.width, size.height);
                params.setPreviewFormat(ImageFormat.NV21);
                params.setFlashMode(flash_on ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);

                camera.setParameters(params);

                int bufferSize = size.width * size.height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
                previewBuffer = new byte[bufferSize];

                surfaceTexture = new SurfaceTexture(10); // Texture id can be arbitrary
                camera.setPreviewTexture(surfaceTexture);
                camera.addCallbackBuffer(previewBuffer);
                camera.setPreviewCallbackWithBuffer(previewCallback);
                camera.startPreview();

                Log.i(getPluginName(), "Camera preview started successfully on SurfaceTexture.");
            } catch (IOException e) {
                Log.e(getPluginName(), "Couldn't start camera: " + e.getMessage());
                isRunning = false;
            } catch (Exception e) {
                Log.e(getPluginName(), "General error starting camera: ", e);
                isRunning = false;
            }
        });
    }

    @UsedByGodot
    public void stopCamera() {
        if (!isRunning) {
            Log.w(getPluginName(), "Camera already stopped.");
            return;
        }
        isRunning = false;
        cameraHandler.post(() -> {
            if (camera != null) {
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
                camera.release();
                camera = null;
            }
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }
            cameraThread.quitSafely();
        });
    }

    @UsedByGodot
    public boolean requestCameraPermissions() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private Camera.Size getClosestPreviewSize(Camera.Parameters params, int width, int height) {
        Camera.Size bestSize = params.getSupportedPreviewSizes().get(0);
        int diff = Integer.MAX_VALUE;
        for (Camera.Size size : params.getSupportedPreviewSizes()) {
            int new_diff = Math.abs(size.width - width) + Math.abs(size.height - height);
            if (new_diff < diff) {
                bestSize = size;
                diff = new_diff;
            }
        }
        return bestSize;
    }

    private final Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        long lastFrameTime = 0;
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            long t = System.currentTimeMillis();
            if (t - lastFrameTime >= capture_interval && isRunning) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                byte[] frameData = data.clone();
                // Send frame data back to godot
                emitSignal("on_camera_frame", yuv2rgb(frameData, size.width, size.height), size.width, size.height);
                lastFrameTime = t;
            }
            if (isRunning) {
                camera.addCallbackBuffer(previewBuffer);
            }
        }
    };

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