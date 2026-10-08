package dev.reactfuscator.cli;

import dev.reactfuscator.config.ConfigParser;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.core.ApplicationFactory;
import dev.reactfuscator.gui.GuiLauncher;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "react-fuscator",
        version = "React-Fuscator 1.1.0",
        description = "Java + ASM obfuscation for JAR, Paper and Fabric.",
        mixinStandardHelpOptions = true,
        subcommands = {ObfuscateCommand.class, InspectCommand.class, VerifyCommand.class})
public final class Launcher implements Callable<Integer> {
    @Spec private Model.CommandSpec spec;

    public static void main(String[] args) {
        CommandLine command =
                new CommandLine(new Launcher()).setCaseInsensitiveEnumValuesAllowed(true);
        command.setExecutionExceptionHandler(
                (error, line, parse) -> {
                    line.getErr().println("ERROR: " + error.getMessage());
                    if (Boolean.getBoolean("reactfuscator.debug")) {
                        error.printStackTrace(line.getErr());
                    }
                    return 1;
                });
        int result = command.execute(args);
        if (result != 0) {
            System.exit(result);
        }
    }

    public Integer call() {
        return gui();
    }

    @Command(name = "gui", description = "Open the React-Fuscator desktop interface")
    public int gui() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            spec.commandLine().usage(System.out);
            return 0;
        }
        new GuiLauncher(new ApplicationFactory()).open();
        return 0;
    }

    @Command(name = "init-config", description = "Write a documented-shape JSON config")
    public int init(@Parameters(index = "0") Path path) throws Exception {
        new ConfigParser().write(path, new ObfuscationConfig());
        return 0;
    }

    @Command(
            name = "transformers",
            description = "List registered transformer IDs and descriptions")
    public int transformers() {
        new ApplicationFactory()
                .registry()
                .ordered()
                .forEach(
                        t ->
                                spec.commandLine()
                                        .getOut()
                                        .println(
                                                t.descriptor().id()
                                                        + " — "
                                                        + t.descriptor().description()));
        return 0;
    }

    @Command(name = "retrace", description = "Recover class/method names in a stack trace")
    public int retrace(@Parameters(index = "0") Path mapping, @Parameters(index = "1") Path trace)
            throws Exception {
        spec.commandLine()
                .getOut()
                .print(
                        new dev.reactfuscator.mapping.RetraceService()
                                .retrace(mapping, java.nio.file.Files.readString(trace)));
        return 0;
    }
}
