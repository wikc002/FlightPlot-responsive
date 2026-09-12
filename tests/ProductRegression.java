import me.drton.flightplot.export.AbstractTrackExporter;
import me.drton.flightplot.export.TrackPoint;
import me.drton.flightplot.export.TrackReader;
import me.drton.flightplot.export.TrackExporterConfiguration;
import me.drton.flightplot.processors.Expression;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class ProductRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FailingExporter extends AbstractTrackExporter {
        protected void writeStart() throws IOException { throw new IOException("expected write failure"); }
        protected void writeTrackPartStart(String name) throws IOException {}
        protected void writePoint(TrackPoint point) throws IOException {}
        protected void writeTrackPartEnd() throws IOException {}
        protected void writeEnd() throws IOException {}
        public String getName() { return "FAIL"; }
        public String getDescription() { return "Failure test"; }
        public String getFileExtension() { return "tmp"; }
    }

    public static void main(String[] args) throws Exception {
        TrackExporterConfiguration configuration = new TrackExporterConfiguration();
        configuration.setSplitTracksByFlightMode(true);
        check(configuration.isSplitTracksByFlightMode(), "split-track option must be stored");
        configuration.setSplitTracksByFlightMode(false);
        check(!configuration.isSplitTracksByFlightMode(), "split-track option must be clearable");
        File output=File.createTempFile("flightplot-export-",".tmp");
        try {
            boolean failed=false;
            try { new FailingExporter().export(() -> null,configuration,output,"Track"); }
            catch(IOException expected) { failed=true; }
            check(failed,"track exporter must report write failures");
        } finally { output.delete(); }

        Expression expression=new Expression();
        expression.setFieldsList(Collections.singletonMap("ATT.Roll","float"));
        expression.setParameters(Collections.<String,Object>singletonMap("Expression","("));
        boolean invalidExpressionReported=false;
        try { expression.init(); } catch(IllegalArgumentException expected) { invalidExpressionReported=true; }
        check(invalidExpressionReported,"invalid expression must reach the UI error path");
        System.out.println("PASS track option state, export error propagation and expression validation");
    }
}
