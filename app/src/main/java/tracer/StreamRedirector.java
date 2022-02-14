/**
 * Utility class to redirect stdin to a captive output stream, or vice versa.
 */

package tracer;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redirect InputStream to OutputStream on a loop.
 */
public class StreamRedirector implements AutoCloseable {
    private final Thread thread;

    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * Redirect from System.in to output (target program stdin).
     */
    public StreamRedirector(InputStream input, OutputStream output) {
        thread = new Thread(() -> redirectOnLoop(input, output));
        thread.start();
    }

    /**
     * Run a separate thread to redirect the input.
     *
     * @param input        Input stream to wait for.
     * @param output       Output stream to redirect to.
     * @param waitForInput Whether to wait indefinitely when input is done, or close the thread
     *                     immediately.
     */
    void redirectOnLoop(InputStream input, OutputStream output) {
        byte[] buf = new byte[1024];
        try {
            // query input on a loop
            while (isRunning.get()) {
                if (input.available() == 0) {
                    if (input != System.in) {
                        continue;
                    } else {
                        break;
                    }
                }
                int n;
                if ((n = input.read(buf)) > -1) {
                    byte[] slice = Arrays.copyOfRange(buf, 0, n);
                    output.write(slice);
                    output.flush();
                }
            }
            if (input != System.in) {
                input.close();
            }
            if (output != System.out) {
                output.close();
            }
        } catch (Exception ex) {
            System.out.println("Error redirecting: " + ex + " " + input + " " + output);
            ex.printStackTrace();
        }
    }

    /**
     * Close this resource, cleaning up all streams and threads.
     */
    public void close() throws Exception {
        isRunning.set(false);
        thread.join();
    }
}