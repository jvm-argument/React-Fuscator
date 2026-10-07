package dev.reactfuscator.testing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Optional real-server harness. Not run by unit tests; commands are sent only to the owned child process. */
public final class RuntimeSmokeRunner {
    public static void main(String[] args) throws Exception {
        Path server=Path.of(args[0]).toAbsolutePath();Path executable=args.length>2?Path.of(args[2]):Path.of(System.getProperty("java.home"),"bin","java.exe");Path log=server.resolve("runtime-smoke.log");
        Process process=new ProcessBuilder(executable.toString(),"-Xms512M","-Xmx2G","-Xverify:all","-Dterminal.jline=false","-Dterminal.ansi=false","-DPaper.IgnoreJavaVersion=true","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar","server.jar","nogui").directory(server.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try(Writer input=new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8)) {
            waitFor(log,"Done (",process,120);
            System.out.println("Server ready; exercising plugin commands");
            for(String command:Files.readAllLines(Path.of(args[1]),StandardCharsets.UTF_8)) if(!command.isBlank()) { input.write(command+"\n");input.flush();Thread.sleep(2000); }
            Thread.sleep(5000);input.write("stop\n");input.flush();
            if(!process.waitFor(60,TimeUnit.SECONDS))throw new IllegalStateException("Server failed to stop");
            String text=Files.readString(log);System.out.println(text.substring(Math.max(0,text.length()-14000)));
            if(process.exitValue()!=0)throw new IllegalStateException("Server exit "+process.exitValue());
        } finally { if(process.isAlive())process.destroyForcibly(); }
    }
    private static void waitFor(Path log,String token,Process process,int seconds) throws Exception {
        long end=System.nanoTime()+Duration.ofSeconds(seconds).toNanos();
        while(System.nanoTime()<end) {if(Files.exists(log) && Files.readString(log).contains(token))return;if(!process.isAlive())throw new IllegalStateException(Files.readString(log));Thread.sleep(250);}
        throw new IllegalStateException("Startup timed out; inspect "+log);
    }
}
