package tracer;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
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
    BufferedWriter traceWriter;

    StreamRedirector inputRedirector;
    StreamRedirector outputRedirector;

    HashMap<Location, HashMap<String, String>> framesToVariableValues;

    /**
     * Construct a Tracer targeting a certain class and method by name.
     * Usually, className = "Main" and methodName = "main".
     */
    public Tracer(OutputStream trace, OutputStream log, String className, String methodName) throws Exception {
        initVmEnvironment(className);
        this.methodName = methodName;
        traceWriter = new BufferedWriter(new OutputStreamWriter(trace));
        inputRedirector = new StreamRedirector(vm.process().getInputStream(), log);
        outputRedirector = new StreamRedirector(System.in, vm.process().getOutputStream());
        traceWriter.append("<trace>\n");

        framesToVariableValues = new HashMap<>();
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
        traceWriter.append("</trace>\n");
        traceWriter.close();
        inputRedirector.close();
        outputRedirector.close();
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
        else if (event instanceof BreakpointEvent) {
            event.request().disable();
            BreakpointEvent evt = (BreakpointEvent) event;
            displayVariables(evt);
            enableStepRequest(evt);
        }

        // third called over and over
        else if (event instanceof StepEvent) {
            StepEvent evt = (StepEvent) event;
            displayVariables(evt);
        }
        
        else if (event instanceof VMStartEvent) { }
        else if (event instanceof VMDeathEvent) { }
        else if (event instanceof VMDisconnectEvent) { }

        else {
            System.out.printf("Could not handle %s\n", event.getClass().getName());
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
            Location location = m.location();
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
        Location frameLocation = stackFrame.location().method().location();
        if (stackFrame.location().toString().contains(debugClass)) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(
                    stackFrame.visibleVariables());
            traceWriter.append(String.format("<program_point filename=\"%s\" line=\"%s\">\n",
                    stackFrame.location().sourcePath(), stackFrame.location().lineNumber()));
            if (!framesToVariableValues.containsKey(frameLocation)) {
                framesToVariableValues.put(frameLocation, new HashMap<>());
            }
            try {
                HashMap<String, String> variableMap = framesToVariableValues.get(frameLocation);

                for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                    LocalVariable localVariable = entry.getKey();
                    Value value = entry.getValue();

                    String proxy = null;
                    String valueString;
                    String variableType;
                    String variableName = localVariable.name();

                    try {
                        if (value instanceof ArrayReference) {
                            ArrayReference arr = ((ArrayReference) value);
                            variableType = arr.getClass().getName();
                            valueString = arr.getValues().toString();
                        } else if (value instanceof ObjectReference) {
                            // https://stackoverflow.com/a/59012879/8999671
                            // Method callMethod gets its ThreadReference from the event in this
                            // example.
                            // https://github.com/SpoonLabs/nopol/blob/master/nopol/src/main/java/fr/inria/lille/repair/synthesis/collect/DynamothDataCollector.java#L428

                            ObjectReference objectReference = ((ObjectReference) value);
                            variableType = objectReference.referenceType().name();

                            // TODO: Do direct type comparison instead of string comparison
                            if (objectReference.referenceType().name().equals("java.util.Scanner")) {
                                proxy = "objectReference.referenceType().name()";
                                valueString = objectReference.referenceType().name();
                            }
                            else {
                                Method toStringMethod = objectReference.referenceType().methodsByName(
                                        "toString", "()Ljava/lang/String;").get(0);
                                proxy = toStringMethod.toString();
                                valueString = objectReference.invokeMethod(event.thread(),
                                        toStringMethod, Collections.emptyList(),
                                        ObjectReference.INVOKE_SINGLE_THREADED).toString();
                            }
                        } else {
                            if (value == null) {
                                variableType = null;
                                valueString = null;
                            }
                            else {
                                variableType = value.getClass().getName();
                                valueString = value.toString();
                            }
                        }

                        String age = "old";
                        if (!variableMap.containsKey(variableName)) {
                            age = "new";
                        }
                        else if (!variableMap.get(variableName).equals(valueString)) {
                            age = "modified";
                        }

                        variableMap.put(variableName, valueString);

                        String proxyStr = "";
                        if (proxy != null) proxyStr = "proxy=\"" + proxy + "\"";
                        traceWriter.append(String.format(
                                "<variable type=\"%s\" age=\"%s\" name=\"%s\" " +
                                        "%s>%s</variable>\n",
                                variableType, age, variableName,
                                proxyStr, valueString));
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.printf("Exception for variable %s\n", localVariable.name());
                        throw e;
                    }
                }
            } finally {
                traceWriter.append("</program_point>\n");
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
                    StepRequest.STEP_LINE, StepRequest.STEP_INTO);
            stepRequest.addClassFilter(debugClass);
            stepRequest.enable();
        }
    }
}
