package tracer;

import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.VMDisconnectedException;

public class App
{
    public static void main(String[] args) throws Exception
    {
        if (args.length < 2)
        {
            System.out.println("Usage: App.java <classPattern> <methodName>");
            return;
        }
        
        String classPattern = args[0];
        String methodName = args[1];

        System.out.printf("Analyzing %s.%s()\n", classPattern, methodName);

        Tracer debuggerInstance = new Tracer(classPattern, methodName);
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
            debuggerInstance.close();
        }
    }
}