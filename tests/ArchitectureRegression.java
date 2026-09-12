package me.drton.flightplot;

import me.drton.flightplot.processors.ProcessorsList;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ArchitectureRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Workspace workspace = new Workspace();
        ChartTab first = new ChartTab("first"), second = new ChartTab("second"), third = new ChartTab("third");
        check(workspace.add(first) == 0 && workspace.current() == first, "first page owns current state");
        workspace.add(second); workspace.add(third); workspace.select(1);
        workspace.remove(first);
        check(workspace.current() == second && workspace.currentIndex() == 0, "removal preserves selected page identity");
        workspace.remove(second);
        check(workspace.current() == third && workspace.size() == 1, "selected page removal chooses remaining page");

        DefaultTableModel model = new DefaultTableModel(new Object[]{"Enabled", "Processor"}, 0);
        Map<String,Object> parameters = new HashMap<String,Object>();
        parameters.put("Fields", "ATT.Roll ATT.Pitch"); parameters.put("LPF", "0.0"); parameters.put("Scale", "1.0"); parameters.put("Offset", "0.0");
        Map<String,Color> colors = new HashMap<String,Color>();
        colors.put("ATT.Roll", Color.BLACK); colors.put("ATT.Pitch", Color.RED);
        model.addRow(new Object[]{true, new ProcessorPreset("att", "Simple", parameters, colors, true)});
        ProcessorSelectionMemory memory = new ProcessorSelectionMemory();
        memory.remember(LogService.DATAFLASH_BIN, model);
        Map<String,String> schema = new LinkedHashMap<String,String>(); schema.put("ATT.Roll", "float");
        ProcessorSelectionMemory.Result restored = memory.adapt(memory.forType(LogService.DATAFLASH_BIN), schema, new ProcessorsList(), "zh_CN");
        check(restored.processors.size() == 1 && restored.skippedFields == 1, "selection adapts to dynamic schema");
        check("ATT.Roll".equals(restored.processors.get(0).getParameters().get("Fields")), "missing field removed");
        check(Color.BLACK.equals(restored.processors.get(0).getColors().get("ATT.Roll")), "field color survives adaptation");

        LogService logs = new LogService();
        check(logs.supports("A.BIN") && logs.supports("b.log") && logs.supports("c.ulg") && !logs.supports("d.txt"), "one format gate");

        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame owner = new JFrame();
            DialogCoordinator dialogs = new DialogCoordinator(owner);
            try {
                check(dialogs.claim("confirm"), "first dialog claim succeeds");
                check(!dialogs.claim("confirm"), "duplicate dialog claim is rejected before construction");
                dialogs.release("confirm", null);
                check(dialogs.claim("confirm"), "dialog claim is reusable after release");
                dialogs.release("confirm", null);
            } catch (Throwable error) { failure.set(error); }
            finally { owner.dispose(); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        System.out.println("PASS single workspace, selection memory, format gate and dialog claims");
    }
}
