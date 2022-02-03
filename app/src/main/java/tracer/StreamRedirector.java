/**
 * Utility class to redirect stdin to a captive output stream, or vice versa.
 */

package tracer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamRedirector implements AutoCloseable {
    private Thread thread;

    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    public StreamRedirector(OutputStream output)
    {
        InputStream input = System.in;
        thread = new Thread(() -> redirectOnLoop(input, output, false));
        thread.start();
    }

    public StreamRedirector(InputStream input)
    {
        OutputStream output = System.out;
        thread = new Thread(() -> redirectOnLoop(input, output, true));
        thread.start();
    }

    void redirectOnLoop(InputStream input, OutputStream output, boolean waitForInput)
    {
        byte[] buf = new byte[1024];
        try{
            // query input on a loop
            while (isRunning.get())
            {
                if (input.available() == 0)
                {
                    if (waitForInput)
                    {
                        continue;
                    }
                    else
                    {
                        break;
                    }
                    // System.out.printf("Close %s -> %s\n", input, output);
                }
                int n;
                if ((n = input.read(buf)) > -1)
                {
                    // System.out.printf("Read %d \"%s\"\n", n, new String(buf));
                    output.write(buf, 0, n);
                    output.flush();
                }
            }
            if (input != System.in)
            {
                input.close();
            }
            if (output != System.out)
            {
                output.close();
            }
        }
        catch (Exception ex)
        {
            System.out.println("Error redirecting: " + ex + " " + input + " " + output);
        }
    }

    public void close() throws Exception
    {
        isRunning.set(false);
        thread.join();
    }
}