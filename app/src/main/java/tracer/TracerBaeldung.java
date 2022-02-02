/**
 * https://www.baeldung.com/java-debug-interface
 */
package tracer;

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
import java.util.Map;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;

public class TracerBaeldung {
    private int[] breakPointLines;
    private String debugClass;
    private String methodName;

    VirtualMachine vm;
    EventRequestManager erm;
    StreamRedirector inOut;
    StreamRedirector outIn;

    public TracerBaeldung(String className, String methodName) throws IOException, IllegalConnectorArgumentsException, VMStartException
    {
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        LaunchingConnector lc = vmm.defaultConnector();
        Map<String, Connector.Argument> env = lc.defaultArguments();
        env.get("main").setValue(className);
        vm = lc.launch(env);

        inOut = new StreamRedirector(new OutputStreamWriter(vm.process().getOutputStream()));
        outIn = new StreamRedirector(new InputStreamReader(vm.process().getInputStream()));

        // request prepare event for given class pattern
        erm = vm.eventRequestManager();
        ClassPrepareRequest r = erm.createClassPrepareRequest();
        r.addClassFilter(className);
        r.enable();

        this.methodName = methodName;
    }

    public void setDebugClass(String debugClass) {
        this.debugClass = debugClass;
    }

    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    /**
     * Displays the visible variables
     * @param event
     * @throws IncompatibleThreadStateException
     * @throws AbsentInformationException
     */
    public void displayVariables(LocatableEvent event) throws IOException, IncompatibleThreadStateException, AbsentInformationException {
        // System.out.println("Event:" + event);
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
    public void enableStepRequest(BreakpointEvent event) {
        //enable step request for last break point
        if(event.location().toString().contains(debugClass+":"+breakPointLines[breakPointLines.length-1])) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();    
        }
    }

    public void handleEvent(Event event) throws IOException, AbsentInformationException, IllegalConnectorArgumentsException, IncompatibleThreadStateException
    {
        // first called
        if (event instanceof ClassPrepareEvent) {
            ClassPrepareEvent evt = (ClassPrepareEvent)event;

            ClassType classType = (ClassType) evt.referenceType();
            setDebugClass(classType.name());
            
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
                    setBreakPointLines(new int[]{location.lineNumber()});
                    BreakpointRequest bpReq = erm.createBreakpointRequest(location);
                    bpReq.enable();
                }
            });
        }

        // second called
        if (event instanceof BreakpointEvent) {
            event.request().disable();
            BreakpointEvent evt = (BreakpointEvent)event;
            displayVariables(evt);
            enableStepRequest(evt);
        }

        // third called over and over
        if (event instanceof StepEvent) {
            StepEvent evt = (StepEvent)event;
            displayVariables(evt);
        }
        vm.resume();
    }

    public EventSet popEventSet() throws InterruptedException
    {
        return vm.eventQueue().remove();
    }

    public void shutdown() throws InterruptedException
    {
        this.inOut.shutdown();
        this.outIn.shutdown();
    }

    public static void main(String[] args) throws Exception
    {
        String classPattern = args[0];
        String methodName = args[1];

        System.out.println(classPattern + " " + methodName);

        TracerBaeldung debuggerInstance = new TracerBaeldung(classPattern, methodName);
        
        try
        {
            EventSet eventSet = null;
            while ((eventSet = debuggerInstance.popEventSet()) != null) {
                for (Event event : eventSet) {
                    debuggerInstance.handleEvent(event);
                }
            }
        }
        catch (VMDisconnectedException e)
        {
            System.out.println("Virtual Machine is disconnected.");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        } 
        finally
        {
            debuggerInstance.shutdown();
        }
    }
}