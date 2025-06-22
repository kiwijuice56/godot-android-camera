# godot-android-camera
A Godot 4.4+ plugin that provides in-game access to an Android device's camera. API is currently limited, but allows for sampling raw data from the camera at regular intervals (i.e to display camera output in real time).

## Usage
Build the plugin (using `./gradlew build` in the root directory) and copy the output folder (in `plugin/demo/addons/`) into your Godot project's addons folder. After activating the plugin, you can use the `AndroidCamera` node to access the camera:

```
extends Node

var android_camera: AndroidCamera

func _ready() -> void:
	android_camera = AndroidCamera.new()

	android_camera.camera_frame.connect(_on_camera_frame)

	%CheckCameraPermissionsButton.pressed.connect(_on_check_camera_permissions)
	%StartCapturingButton.pressed.connect(_on_start_capturing_pressed)
	%StopCapturingButton.pressed.connect(_on_stop_capturing_pressed)

func _on_camera_frame(timestamp: int, image_texture: ImageTexture) -> void:
	%Canvas.texture = image_texture

func _on_check_camera_permissions() -> void:
	android_camera.request_camera_permissions()

func _on_start_capturing_pressed() -> void:
	android_camera.start_camera()

func _on_stop_capturing_pressed() -> void:
	android_camera.stop_camera()

```

