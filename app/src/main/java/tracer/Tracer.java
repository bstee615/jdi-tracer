package tracer;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Trace a class/method with JDI
 * Adapted from https://www.baeldung.com/java-debug-interface
 */
public class Tracer implements AutoCloseable {
    private int[] breakPointLines;
    private String debugClass;
    private final String methodName;

    VirtualMachine vm;
    EventRequestManager erm;
    StreamRedirector inOut;
    StreamRedirector outIn;
    BufferedWriter writer;

    /**
     * Construct a Tracer targeting a certain class and method by name.
     * Usually, className = "Main" and methodName = "main".
     */
    public Tracer(String logFileName, String className, String methodName) throws Exception {
        initVmEnvironment(className);
        this.methodName = methodName;
        writer = new BufferedWriter(new FileWriter(logFileName));
        inOut = new StreamRedirector(writer, vm.process().getOutputStream());
        outIn = new StreamRedirector(writer, vm.process().getInputStream());
        writer.append("<trace>\n");
    }

    /**
     * Initialize the debug VM.
     */
    public void initVmEnvironment(String className) throws Exception {
        // init VirtualMachine
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        LaunchingConnector lc = vmm.defaultConnector();
        Map<String, Connector.Argument> env = lc.defaultArguments();
        env.get("main").setValue(className);
        vm = lc.launch(env);

        // init EventRequestManager
        erm = vm.eventRequestManager();
        ClassPrepareRequest r = erm.createClassPrepareRequest();
        r.addClassFilter(className);
        r.enable();
    }

    /**
     * Set the name of the class for which we will request a breakpoint.
     */
    public void setDebugClassName(String debugClass) {
        this.debugClass = debugClass;
    }

    /**
     * Set the line numbers on which we will request a breakpoint.
     */
    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    /**
     * Get an EventSet from the VM.
     * If it returns null, then it timed out waiting for an EventSet.
     */
    public EventSet popEventSet() throws InterruptedException {
        return vm.eventQueue().remove();
    }

    /**
     * Close this resource, closing down input redirection threads.
     */
    public void close() throws Exception {
        writer.append("</trace>\n");
        writer.close();
        inOut.close();
        outIn.close();
    }

    /**
     * Handle a single event from the VM.
     */
    public void handleEvent(Event event) throws Exception {
        // first called
        if (event instanceof ClassPrepareEvent) {
            ClassPrepareEvent evt = (ClassPrepareEvent) event;
            setBreakpoint(evt);
        }

        // second called
        if (event instanceof BreakpointEvent) {
            event.request().disable();
            BreakpointEvent evt = (BreakpointEvent) event;
            displayVariables(evt);
            enableStepRequest(evt);
        }

        // third called over and over
        if (event instanceof StepEvent) {
            StepEvent evt = (StepEvent) event;
            displayVariables(evt);
        }

        vm.resume();
    }

    /* debugger actions */

    /**
     * Set a breakpoint on the first line of method methodName.
     */
    public void setBreakpoint(ClassPrepareEvent event) {
        ClassType classType = (ClassType) event.referenceType();
        setDebugClassName(classType.name());

        // Set breakpoint on method by name
        classType.methodsByName(methodName).forEach(m -> {
            List<Location> locations = null;
            try {
                locations = m.allLineLocations();
            } catch (AbsentInformationException e) {
                e.printStackTrace();
            }
            // get the first line location of the function and enable the break point
            assert locations != null;
            Location location = locations.get(0);
            setBreakPointLines(new int[]{location.lineNumber()});
            BreakpointRequest bpReq = erm.createBreakpointRequest(location);
            bpReq.enable();
        });
    }

    /**
     * Displays the visible variables at the current program point.
     */
    public void displayVariables(LocatableEvent event) throws Exception {
        StackFrame stackFrame = event.thread().frame(0);
        if (stackFrame.location().toString().contains(debugClass)) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(
                    stackFrame.visibleVariables());
            writer.append(String.format("<program_point location=\"%s\">\n",
                    stackFrame.location().toString()));
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                LocalVariable localVariable = entry.getKey();
                Value value = entry.getValue();
                try {
                    if (value instanceof ArrayReference) {
                        ArrayReference arr = ((ArrayReference) value);
                        writer.append(
                                String.format("<variable type=\"%s\" name=\"%s\">%s</variable>\n",
                                        arr.getClass().getName(), localVariable.name(),
                                        arr.getValues().toString()));
                    } else if (value instanceof ObjectReference) {
                        // https://stackoverflow.com/a/59012879/8999671
                        // Method callMethod gets its ThreadReference from the event in this
                        // example.
                        // https://github.com/SpoonLabs/nopol/blob/master/nopol/src/main/java/fr/inria/lille/repair/synthesis/collect/DynamothDataCollector.java#L428
                        ObjectReference objectReference = ((ObjectReference) value);
                        Method toStringMethod = objectReference.referenceType().methodsByName(
                                "toString").get(0);
                        String valueString = objectReference.invokeMethod(event.thread(),
                                toStringMethod, Collections.emptyList(),
                                ObjectReference.INVOKE_SINGLE_THREADED).toString();
                        writer.append(String.format(
                                "<variable type=\"%s\" name=\"%s\" proxy=\"%s\">%s</variable>\n",
                                objectReference.referenceType().name(), localVariable.name(),
                                toStringMethod.toString(), valueString));
                    } else {
                        writer.append(
                                String.format("<variable type=\"%s\" name=\"%s\">%s</variable>\n",
                                        (value == null) ? null : value.getClass().getName(),
                                        localVariable.name(), value));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.printf("Exception for variable %s\n", localVariable.name());
                    throw e;
                } finally {
                    writer.append("</program_point>\n");
                }
            }
        }
    }

    /**
     * Requests to step forward from a breakpoint.
     */
    public void enableStepRequest(BreakpointEvent event) {
        //enable step request for last break point
        if (event.location().toString().contains(
                debugClass + ":" + breakPointLines[breakPointLines.length - 1])) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(),
                    StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();
        }
    }
}