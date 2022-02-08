package tracer;

import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.VMDisconnectedException;

public class App {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: App.java <classPattern> <methodName>");
            return;
        }

        String outputFileName = "log.xml";
        for (int i = 0; i < args.length; i ++) {
            if (args[i].equals("-o")) {
                assert i < args.length - 1;
                outputFileName = args[i+1];
                assert outputFileName.endsWith(".xml");
            }
        }

        String classPattern = args[0];
        String methodName = args[1];

        System.out.printf("Analyzing %s.%s()\n", classPattern, methodName);

        try (Tracer debuggerInstance = new Tracer(outputFileName, classPattern, methodName)) {
            EventSet eventSet;
            while ((eventSet = debuggerInstance.popEventSet()) != null) {
                for (Event event : eventSet) {
                    debuggerInstance.handleEvent(event);
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}