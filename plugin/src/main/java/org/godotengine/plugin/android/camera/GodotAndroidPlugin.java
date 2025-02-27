package org.godotengine.plugin.android.camera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Log;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.UsedByGodot;

import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

public class GodotAndroidPlugin extends GodotPlugin {
    private static final int CAMERA_REQUEST = 1001;
    private static final int CAMERA_PERMISSION_REQUEST = 1002;

    private Activity activity;

    public GodotAndroidPlugin(Godot godot) {
        super(godot);
        this.activity = godot.getActivity();
    }

    @UsedByGodot
    public void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivityForResult(cameraIntent, CAMERA_REQUEST);
        }
    }

    @UsedByGodot
    public boolean requestCameraPermissions() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
        boolean granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        return granted;
    }

    @UsedByGodot
    public boolean isCameraAvailable() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        return cameraIntent.resolveActivity(activity.getPackageManager()) != null;
    }

    @Override
    public String getPluginName() {
        return "GodotAndroidCamera";
    }
}