/**
 * https://www.baeldung.com/java-debug-interface
 */

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.Method;
import java.util.function.Consumer;
import java.util.List;

public class TracerBaeldung {

    private int[] breakPointLines;
    private String debugClass;

    public TracerBaeldung()
    {
    }

    public String getDebugClass() {
        return debugClass;
    }

    public void setDebugClass(String debugClass) {
        this.debugClass = debugClass;
    }

    public int[] getBreakPointLines() {
        return breakPointLines;
    }

    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    /**
     * Sets the debug class as the main argument in the connector and launches the VM
     * @return VirtualMachine
     * @throws IOException
     * @throws IllegalConnectorArgumentsException
     * @throws VMStartException
     */
    public VirtualMachine connectAndLaunchVM() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass);
        VirtualMachine vm = launchingConnector.launch(arguments);
        return vm;
    }

    /**
     * Creates a request to prepare the debug class, add filter as the debug class and enables it
     * @param vm
     */
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass);
        classPrepareRequest.enable();
    }

    /**
     * Sets the break points at the line numbers mentioned in breakPointLines array
     * @param vm
     * @param event
     * @throws AbsentInformationException
     */
    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
        ClassType classType = (ClassType) event.referenceType();
        for(int lineNumber: breakPointLines) {
            Location location = classType.locationsOfLine(lineNumber).get(0);
            BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
            bpReq.enable();
        }
    }

    /**
     * Displays the visible variables
     * @param event
     * @throws IncompatibleThreadStateException
     * @throws AbsentInformationException
     */
    public void displayVariables(LocatableEvent event) throws IncompatibleThreadStateException, AbsentInformationException {
        StackFrame stackFrame = event.thread().frame(0);
        if(stackFrame.location().toString().contains(debugClass)) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            System.out.println("Variables at " +stackFrame.location().toString() +  " > ");
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                System.out.println(entry.getKey().name() + " = " + entry.getValue());
            }
        }
    }

    /**
     * Enables step request for a break point
     * @param vm
     * @param event
     */
    public void enableStepRequest(VirtualMachine vm, BreakpointEvent event) {
        //enable step request for last break point
        if(event.location().toString().contains(debugClass+":"+breakPointLines[breakPointLines.length-1])) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();    
        }
    }

    public static void main(String[] args) throws Exception
    {
        String main;
        String classPattern;
        main = classPattern = args[0];
        String methodName = args[1];

        System.out.println(main + " " + classPattern + " " + methodName);

        TracerBaeldung debuggerInstance = new TracerBaeldung();

        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        LaunchingConnector lc = vmm.defaultConnector();
        Map<String, Connector.Argument> env = lc.defaultArguments();
        env.get("main").setValue(main);
        VirtualMachine vm = lc.launch(env);

        // request prepare event for given class pattern
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest r = erm.createClassPrepareRequest();
        r.addClassFilter(classPattern);
        r.enable();

        try {
            EventSet eventSet = null;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    // first called
                    if (event instanceof ClassPrepareEvent) {
                        ClassPrepareEvent evt = (ClassPrepareEvent)event;
                        ClassType classType = (ClassType) evt.referenceType();
                        debuggerInstance.setDebugClass(classType.name());
                        // Set breakpoint on method by name
                        classType.methodsByName(methodName).forEach(new Consumer<Method>() {
                            @Override
                            public void accept(Method m) {
                                List<Location> locations = null;
                                try {
                                    locations = m.allLineLocations();
                                } catch (AbsentInformationException ex) {
                                    System.out.println(ex);
                                }
                                // get the first line location of the function and enable the break point
                                Location location = locations.get(0);
                                debuggerInstance.setBreakPointLines(new int[]{location.lineNumber()});
                                BreakpointRequest bpReq = erm.createBreakpointRequest(location);
                                bpReq.enable();
                            }
                        });
                    }

                    // second called
                    if (event instanceof BreakpointEvent) {
                        event.request().disable();
                        debuggerInstance.displayVariables((BreakpointEvent) event);
                        debuggerInstance.enableStepRequest(vm, (BreakpointEvent)event);
                    }

                    // third called over and over
                    if (event instanceof StepEvent) {
                        debuggerInstance.displayVariables((StepEvent) event);
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
        } 
        finally {
            InputStreamReader reader = new InputStreamReader(vm.process().getInputStream());
            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            char[] buf = new char[512];

            reader.read(buf);
            writer.write(buf);
            writer.flush();
        }

    }

}