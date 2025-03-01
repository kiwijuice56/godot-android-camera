@tool
extends EditorPlugin

var export_plugin: AndroidExportPlugin

func _enter_tree() -> void:
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)

	export_plugin.connect("on_video_recorded", _on_video_recorded)

func _exit_tree() -> void:
	# Clean-up of the plugin goes here.
	remove_export_plugin(export_plugin)
	export_plugin = null

func _on_video_recorded(video_path: String) -> void:
	print("Video saved at: ", video_path)

class AndroidExportPlugin extends EditorExportPlugin:
	var _plugin_name = "GodotAndroidCamera"

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
		return PackedStringArray(["androidx.core:core:1.9.0"])

	func _get_name():
		return _plugin_name
