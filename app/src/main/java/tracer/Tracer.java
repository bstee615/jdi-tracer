package tracer;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
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

/**
 * Trace a class/method with JDI
 * Adapted from https://www.baeldung.com/java-debug-interface
 */
public class Tracer implements AutoCloseable {
    private int[] breakPointLines;
    private String debugClass;
    private String methodName;

    VirtualMachine vm;
    EventRequestManager erm;
    StreamRedirector inOut;
    StreamRedirector outIn;

    public Tracer(String className, String methodName) throws Exception
    {
        initVmEnvironment(className);
        this.methodName = methodName;
        inOut = new StreamRedirector(vm.process().getOutputStream());
        outIn = new StreamRedirector(vm.process().getInputStream());
    }

    public void initVmEnvironment(String className) throws Exception
    {
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

    public void setDebugClassName(String debugClass) {
        this.debugClass = debugClass;
    }

    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    public EventSet popEventSet() throws InterruptedException
    {
        EventSet set = vm.eventQueue().remove(60 * 1000);
        // if (set != null)
        // {
        //     System.out.println("popEventSet " + set.size());
        // }
        // else
        // {
        //     System.out.println("popEventSet null");
        // }
        if (set == null)
        {
            System.out.println("Timed out waiting for EventSet");
        }
        return set;
    }

    public void close() throws Exception
    {
        this.inOut.close();
        this.outIn.close();
    }

    /**
     * Handle a single event
     */
    public void handleEvent(Event event) throws Exception
    {
        // System.out.println("handleEvent called! " + event.getClass().getName());
        // first called
        if (event instanceof ClassPrepareEvent) {
            ClassPrepareEvent evt = (ClassPrepareEvent)event;
            setBreakpoint(evt);
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

    /* debugger actions */

    public void setBreakpoint(ClassPrepareEvent event)throws Exception
    {
        ClassType classType = (ClassType) event.referenceType();
        // System.out.println("setBreakpoint called! " + classType.name() + " " + methodName);
        setDebugClassName(classType.name());
        
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
                // System.out.println("method called! " + location.toString());
                setBreakPointLines(new int[]{location.lineNumber()});
                BreakpointRequest bpReq = erm.createBreakpointRequest(location);
                bpReq.enable();
            }
        });
    }

    /**
     * Displays the visible variables
     * @param event
     * @throws IncompatibleThreadStateException
     * @throws AbsentInformationException
     */
    public void displayVariables(LocatableEvent event) throws IOException, AbsentInformationException {
        // System.out.println("displayVariables called!");
        try
        {
            StackFrame stackFrame = event.thread().frame(0);
            if(stackFrame.location().toString().contains(debugClass)) {
                Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
                System.out.println("Variables at " +stackFrame.location().toString());
                for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                    System.out.println(entry.getKey().name() + " = " + entry.getValue());
                }
            }
        }
        catch (IncompatibleThreadStateException ex)
        {

        }
    }

    /**
     * Enables step request for a break point
     * @param vm
     * @param event
     */
    public void enableStepRequest(BreakpointEvent event) {
        // System.out.println("enableStepRequest called! " + event.location().toString());
        //enable step request for last break point
        if(event.location().toString().contains(debugClass+":"+breakPointLines[breakPointLines.length-1])) {
            StepRequest stepRequest = vm.eventRequestManager().createStepRequest(event.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
            stepRequest.enable();    
        }
    }
}