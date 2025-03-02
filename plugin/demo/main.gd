extends Node

var _plugin_name: String = "GodotAndroidCamera"
var _plugin_singleton: JNISingleton

func _ready() -> void:
	# Initialize plugin
	if Engine.has_singleton(_plugin_name):
		_plugin_singleton = Engine.get_singleton(_plugin_name)
	else:
		printerr("Initialization error: unable to access the java logic")
	
	_plugin_singleton.connect("on_camera_frame", _on_camera_frame)
	
	%CheckCameraPermissionsButton.pressed.connect(_on_check_camera_permissions)
	%StartCapturingButton.pressed.connect(_on_start_capturing_pressed)
	%StopCapturingButton.pressed.connect(_on_stop_capturing_pressed)

func _on_camera_frame(data: PackedByteArray, width: int, height: int) -> void:
	var image: Image = Image.create_from_data(width, height, false, Image.FORMAT_RGB8, data,)
	%Canvas.texture = ImageTexture.create_from_image(image)

func _on_check_camera_permissions() -> void:
	_plugin_singleton.requestCameraPermissions()

func _on_start_capturing_pressed() -> void:
	_plugin_singleton.startCamera(1024, 1024, 50, false)

func _on_stop_capturing_pressed() -> void:
	_plugin_singleton.stopCamera()
