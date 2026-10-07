package dev.reactfuscator.cli;

import dev.reactfuscator.service.ProgressListener;
import java.io.PrintWriter;

public final class ConsoleProgressListener implements ProgressListener {
    private final PrintWriter output;
    private int lastPercent=-10;
    public ConsoleProgressListener(PrintWriter output) { this.output=output; }
    public void progress(double fraction,String message) { int percent=(int)(fraction*100); if(percent>=lastPercent+10 || percent==100) { output.println("["+percent+"%] "+message);output.flush();lastPercent=percent; } }
    public void log(String message) { output.println(message);output.flush(); }
}
