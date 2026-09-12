package me.drton.flightplot;

import me.drton.flightplot.processors.ProcessorsList;

import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores one checked processor selection per log family and adapts it to a new dynamic schema. */
final class ProcessorSelectionMemory {
    static final class Result {
        final List<ProcessorPreset> processors = new ArrayList<ProcessorPreset>();
        int fields;
        int skippedFields;
        int skippedProcessors;

        String notice(boolean chinese) {
            if (processors.isEmpty()) return skippedFields == 0 ? null :
                    (chinese ? "上次所选字段在该日志中不存在，已跳过" :
                            "Previous selected fields are unavailable in this log and were skipped");
            String value = chinese ? "已沿用上次选择：" + processors.size() + " 个处理器，" + fields + " 个字段" :
                    "Restored previous selection: " + processors.size() + " processors, " + fields + " fields";
            if (skippedFields > 0) value += chinese ? "；跳过 " + skippedFields + " 个缺失字段" :
                    "; skipped " + skippedFields + " unavailable fields";
            if (skippedProcessors > 0) value += chinese ? "、" + skippedProcessors + " 个不兼容处理器" :
                    ", " + skippedProcessors + " incompatible processors";
            return value;
        }
    }

    private final Map<String, List<ProcessorPreset>> byLogType = new HashMap<String, List<ProcessorPreset>>();

    List<ProcessorPreset> snapshot(DefaultTableModel model) {
        List<ProcessorPreset> result = new ArrayList<ProcessorPreset>();
        if (model == null) return result;
        for (int row = 0; row < model.getRowCount(); row++) {
            Object value = model.getValueAt(row, 1);
            if (Boolean.TRUE.equals(model.getValueAt(row, 0)) && value instanceof ProcessorPreset) {
                ProcessorPreset copy = ((ProcessorPreset) value).clone();
                copy.setVisible(true);
                result.add(copy);
            }
        }
        return result;
    }

    void remember(String type, DefaultTableModel model) {
        if (type != null) byLogType.put(type, snapshot(model));
    }

    List<ProcessorPreset> forType(String type) { return copy(byLogType.get(type)); }
    List<ProcessorPreset> copy(List<ProcessorPreset> source) {
        List<ProcessorPreset> result = new ArrayList<ProcessorPreset>();
        if (source != null) for (ProcessorPreset preset : source) if (preset != null) result.add(preset.clone());
        return result;
    }

    Result adapt(List<ProcessorPreset> source, Map<String, String> fields, ProcessorsList types, String language) {
        Result result = new Result();
        if (source == null || fields == null) return result;
        for (ProcessorPreset original : source) {
            ProcessorPreset candidate = adaptOne(original, fields, result);
            if (candidate == null) continue;
            try {
                if (types.getProcessorInstance(candidate, 0.0, fields) == null) continue;
            } catch (Exception invalid) { result.skippedProcessors++; continue; }
            candidate.setUiLanguage(language);
            candidate.setVisible(true);
            result.processors.add(candidate);
        }
        return result;
    }

    private ProcessorPreset adaptOne(ProcessorPreset original, Map<String, String> fields, Result result) {
        if (original == null || original.getParameters() == null) return null;
        ProcessorPreset candidate = original.clone();
        Map<String, Object> parameters = candidate.getParameters();
        if ("Simple".equals(candidate.getProcessorType())) {
            Object value = parameters.get("Fields");
            List<String> kept = new ArrayList<String>();
            if (value != null) for (String field : value.toString().trim().split("\\s+")) {
                if (field.isEmpty()) continue;
                if (fields.containsKey(field)) { kept.add(field); result.fields++; }
                else result.skippedFields++;
            }
            if (kept.isEmpty()) return null;
            parameters.put("Fields", String.join(" ", kept));
            Map<String, Color> colors = new HashMap<String, Color>();
            for (String field : kept) {
                Color color = candidate.getColors().get(field);
                if (color != null) colors.put(field, color);
            }
            candidate.setColors(colors);
            return candidate;
        }
        int referenced = 0;
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            if (!parameter.getKey().startsWith("Field") || parameter.getValue() == null) continue;
            for (String field : parameter.getValue().toString().trim().split("\\s+")) {
                if (field.isEmpty()) continue;
                referenced++;
                if (!fields.containsKey(field)) {
                    result.skippedFields++;
                    result.skippedProcessors++;
                    return null;
                }
            }
        }
        result.fields += referenced;
        return candidate;
    }
}
