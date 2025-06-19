class_name AndroidCamera extends Node

const _plugin_name: String = "GodotAndroidCamera"

var java_interface: JNISingleton

signal camera_frame(timestamp: int, image_texture: ImageTexture)

func _initialize_java_interface() -> void:
	assert(Engine.has_singleton(_plugin_name))
	java_interface = Engine.get_singleton(_plugin_name)
	java_interface.connect("on_camera_frame", _on_camera_frame)

## Internal signal method.
func _on_camera_frame(timestamp: int, data: PackedByteArray, width: int, height: int) -> void:
	if not is_instance_valid(java_interface):
		_initialize_java_interface()
	
	var image: Image = Image.create_from_data(width, height, false, Image.FORMAT_RGB8, data,)
	camera_frame.emit(timestamp, ImageTexture.create_from_image(image))

## Shows camera permissions pop-up. Required for other camera functions to work.
## Returns true if permissions accepted, false otherwise.
func request_camera_permissions() -> bool:
	if not is_instance_valid(java_interface):
		_initialize_java_interface()
	
	return java_interface.requestCameraPermissions()

## Starts streaming camera frames (via camera_frame signal) with given parameters.
## The highest possible FPS is selected by default.
## The resulting image will be the closest size to (desired_width, desired_height) available.
func start_camera(desired_width: int, desired_height: int, flash_on: bool) -> void:
	if not is_instance_valid(java_interface):
		_initialize_java_interface()
	
	java_interface.startCamera(desired_width, desired_height, flash_on)

## Stops camera streaming.
func stop_camera() -> void:
	if not is_instance_valid(java_interface):
		_initialize_java_interface()
	
	java_interface.stopCamera()
