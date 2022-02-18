package tracer;

import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.VMDisconnectedException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class App {
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 2) {
            System.out.println("Usage: App.java <classPattern> <methodName>");
            return;
        }

        OutputStream traceStream = System.out;
        OutputStream logStream = System.out;
        boolean verbose = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-l":
                    assert i < args.length - 1;
                    String traceFileName = args[i + 1];
                    assert traceFileName.endsWith(".xml");
                    traceStream = new FileOutputStream(traceFileName);
                    break;
                case "-o":
                    assert i < args.length - 1;
                    String logFileName = args[i + 1];
                    assert logFileName.endsWith(".txt");
                    logStream = new FileOutputStream(logFileName);
                    break;
                case "-v":
                    verbose = true;
                    break;
            }
        }

        String classPattern = args[0];
        String methodName = args[1];

        if (verbose) System.out.printf("Analyzing %s.%s()\n", classPattern, methodName);

        try (Tracer debuggerInstance = new Tracer(traceStream, logStream, classPattern, methodName)) {
            EventSet eventSet;
            while ((eventSet = debuggerInstance.popEventSet()) != null) {
                for (Event event : eventSet) {
                    debuggerInstance.handleEvent(event);
                }
            }
        } catch (VMDisconnectedException e) {
            if (verbose) System.out.println("Done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}