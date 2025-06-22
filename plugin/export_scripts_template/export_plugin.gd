@tool
extends EditorPlugin

const _plugin_name: String = "GodotAndroidCamera"

var export_plugin: AndroidExportPlugin

func _enter_tree() -> void:
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)

func _exit_tree() -> void:
	remove_export_plugin(export_plugin)
	export_plugin = null

class AndroidExportPlugin extends EditorExportPlugin:
	func _supports_platform(platform):
		if platform is EditorExportPlatformAndroid:
			return true
		return false

	func _get_android_libraries(platform, debug):
		if debug:
			return PackedStringArray([_plugin_name + "/bin/debug/" + _plugin_name + "-debug.aar"])
		else:
			return PackedStringArray([_plugin_name + "/bin/release/" + _plugin_name + "-release.aar"])

	func _get_android_dependencies(platform, debug):
		return PackedStringArray([
			"androidx.core:core:1.9.0",
			"androidx.camera:camera-lifecycle:1.4.2",
			"androidx.camera:camera-core:1.4.2",
			"androidx.camera:camera-camera2:1.4.2",
			"androidx.camera:camera-view:1.4.2",
		])

	func _get_name():
		return _plugin_name
