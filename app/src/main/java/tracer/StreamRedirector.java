/**
 * Utility class to redirect stdin to a captive output stream, or vice versa.
 */

package tracer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamRedirector {
    private Thread thread;

    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    public StreamRedirector(OutputStreamWriter output)
    {
        InputStreamReader input = new InputStreamReader(System.in);
        thread = new Thread(() -> redirectOnLoop(input, output));
        thread.start();
    }

    public StreamRedirector(InputStreamReader input)
    {
        OutputStreamWriter output = new OutputStreamWriter(System.out);
        thread = new Thread(() -> redirectOnLoop(input, output));
        thread.start();
    }

    void redirectOnLoop(InputStreamReader input, OutputStreamWriter output)
    {
        char[] buf = new char[512];
        try{
            // query input on a loop
            while (isRunning.get())
            {
                if (input.ready())
                {
                    input.read(buf);
                    output.write(buf);
                    output.flush();
                }
            }
            // query input one more time
            if (input.ready())
            {
                input.read(buf);
                output.write(buf);
                output.flush();
            }
        }
        catch (Exception ex)
        {
            System.out.println("Error redirecting: " + ex);
        }
    }

    void shutdown() throws InterruptedException
    {
        isRunning.set(false);
        thread.join();
    }
}