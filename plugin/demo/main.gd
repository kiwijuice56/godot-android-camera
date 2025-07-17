# Example usage of plugin
class_name Main extends Node

var android_camera: AndroidCamera

func _ready() -> void:
	android_camera = AndroidCamera.new()
	
	android_camera.camera_frame.connect(_on_camera_frame)
	
	%CheckCameraPermissionsButton.pressed.connect(_on_check_camera_permissions)
	%StartCapturingButton.pressed.connect(_on_start_capturing_pressed)
	%StopCapturingButton.pressed.connect(_on_stop_capturing_pressed)

func _on_camera_frame(_timestamp: int, data: PackedByteArray, width: int, height: int) -> void:
	if %CanvasEnabled.button_pressed:
		%Canvas.texture = AndroidCamera.raw_data_to_image(data, width, height)

func _on_check_camera_permissions() -> void:
	android_camera.request_camera_permissions()

func _on_start_capturing_pressed() -> void:
	android_camera.start_camera(512, 512, false)

func _on_stop_capturing_pressed() -> void:
	android_camera.stop_camera.call_deferred()
