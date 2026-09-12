package me.drton.flightplot;

import java.util.Locale;

public final class I18n {
    private static String language = "en";

    private I18n() {}

    public static void setLanguage(String lang) {
        language = lang;
    }

    public static String getLanguage() {
        return language;
    }

    public static boolean isZhCN() {
        return "zh_CN".equals(language);
    }

    public static String tr(String key) {
        if (isZhCN()) {
            return trZh(key);
        }
        return trEn(key);
    }

    private static String trZh(String key) {
        switch (key) {
            case "open_log": return "打开日志";
            case "add_processor": return "添加处理器";
            case "remove_processor": return "移除处理器";
            case "remove_all_processors": return "移除全部";
            case "fields": return "字段";
            case "log_info": return "日志信息";
            case "save_preset": return "保存预设";
            case "delete_preset": return "删除预设";
            case "markers": return "标记";
            case "preset": return "预设";
            case "processors": return "处理器";
            case "parameters": return "参数";
            case "log_messages": return "日志消息";
            case "menu_file": return "文件";
            case "menu_view": return "视图";
            case "show_legend": return "显示字段颜色标识";
            case "open_log_menu": return "打开日志...";
            case "import_preset": return "导入预设...";
            case "export_preset": return "导出预设...";
            case "autosave_presets": return "自动保存预设";
            case "export_as_image": return "导出为图片...";
            case "export_track": return "导出轨迹...";
            case "export_parameters": return "导出参数...";
            case "exit": return "退出";
            case "time_mode_log_start": return "日志起始时间";
            case "time_mode_boot": return "开机时间";
            case "time_mode_gps": return "GPS 时间";
            case "enabled": return "启用";
            case "processor": return "处理器";
            case "parameter": return "参数";
            case "value": return "值";
            case "time": return "时间";
            case "level": return "级别";
            case "message": return "消息";
            case "minute": return "分钟";
            case "time_s": return "时间 / Time (s)";
            case "time_utc": return "时间 / Time (UTC)";
            case "value_mixed": return "数值 / Value";
            default: return key;
        }
    }

    private static String trEn(String key) {
        switch (key) {
            case "open_log": return "Open Log";
            case "add_processor": return "Add Processor";
            case "remove_processor": return "Remove Processor";
            case "remove_all_processors": return "Remove All";
            case "fields": return "Fields";
            case "log_info": return "Log Info";
            case "save_preset": return "Save Preset";
            case "delete_preset": return "Delete Preset";
            case "markers": return "Markers";
            case "preset": return "Preset";
            case "processors": return "Processors";
            case "parameters": return "Parameters";
            case "log_messages": return "Log Messages";
            case "menu_file": return "File";
            case "menu_view": return "View";
            case "show_legend": return "Show Field Colors";
            case "open_log_menu": return "Open Log...";
            case "import_preset": return "Import Preset...";
            case "export_preset": return "Export Preset...";
            case "autosave_presets": return "Autosave Presets";
            case "export_as_image": return "Export As Image...";
            case "export_track": return "Export Track...";
            case "export_parameters": return "Export Parameters...";
            case "exit": return "Exit";
            case "time_mode_log_start": return "Log Start Time";
            case "time_mode_boot": return "Boot Time";
            case "time_mode_gps": return "GPS Time";
            case "enabled": return "Enabled";
            case "processor": return "Processor";
            case "parameter": return "Parameter";
            case "value": return "Value";
            case "time": return "Time";
            case "level": return "Level";
            case "message": return "Message";
            case "minute": return "min";
            case "time_s": return "Time (s)";
            case "time_utc": return "Time (UTC)";
            case "value_mixed": return "Value";
            default: return key;
        }
    }
}
