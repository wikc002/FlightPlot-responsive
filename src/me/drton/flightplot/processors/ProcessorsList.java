package me.drton.flightplot.processors;

import me.drton.flightplot.ProcessorPreset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Explicit processor registry; adding a processor has one compile-time checked entry. */
public final class ProcessorsList {
    private final Map<String, Supplier<PlotProcessor>> factories = new LinkedHashMap<String, Supplier<PlotProcessor>>();

    public ProcessorsList() {
        add(Simple::new); add(Derivative::new); add(Abs::new); add(ATan2::new);
        add(PosPIDControlSimulator::new); add(PosRatePIDControlSimulator::new);
        add(PositionEstimator::new); add(GlobalPositionProjection::new); add(LandDetector::new);
        add(Expression::new); add(NEDFromBodyProjection::new); add(Integral::new); add(Battery::new);
        add(PositionEstimatorKF::new); add(EulerFromQuaternion::new); add(Text::new);
    }

    private void add(Supplier<PlotProcessor> factory) {
        PlotProcessor processor = factory.get();
        factories.put(processor.getClass().getSimpleName(), factory);
    }

    public Set<String> getProcessorsList() { return Collections.unmodifiableSet(factories.keySet()); }

    public PlotProcessor getProcessorInstance(ProcessorPreset preset, double skipOut, Map<String, String> fields) {
        Supplier<PlotProcessor> factory = factories.get(preset.getProcessorType());
        if (factory == null) return null;
        PlotProcessor processor = factory.get();
        processor.setSkipOut(skipOut);
        processor.setFieldsList(fields);
        processor.setParameters(preset.getParameters());
        processor.init();
        return processor;
    }
}
