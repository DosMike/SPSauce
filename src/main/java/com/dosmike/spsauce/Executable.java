package com.dosmike.spsauce;

import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.utils.ArgParser;
import com.dosmike.spsauce.utils.UnknownInstructionException;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Executable {

    public static final String UserAgent;

    public enum OperatingSystem {
        Windows,
        Linux,
        Mac,
        Unsupported,
    }

    public static Path scriptdir,cachedir,execdir;
    public static OperatingSystem OS = OperatingSystem.Unsupported;
    public static ExecutorService exec = Executors.newCachedThreadPool();
    public static boolean ARCH64;
    public static Path selfScript;
    private static ArgParser.Flag fStacktrace;
    public static ArgParser.Flag fExtractAll;
    public static ArgParser.Flag fOffline;
    public static ArgParser.Flag fNoExec;
    public static ArgParser.Flag fInteractive;
    public static ArgParser.Flag fInteractiveBatch;
    public static ArgParser.Flag fNoScripts;
    private static ArgParser.Flag fCacheDir;

    static {
        UserAgent = SetupUserAgent();
    }
    private static String SetupUserAgent() {
        String selfVersion;
        try {
            Properties props = new Properties();
            props.load(Executable.class.getClassLoader().getResourceAsStream(".spsmeta"));
            selfVersion = props.getProperty("version");
            if (selfVersion == null || selfVersion.isEmpty()) throw new IllegalStateException();
        } catch (Throwable e) {
            //fallback
            System.err.println("Metadata are corrupt!");
            selfVersion = "1.0";
        }
        return "SPSauce/"+selfVersion+" (by reBane aka DosMike) "+
                "Java/"+System.getProperty("java.version")+" ("+System.getProperty("java.vendor")+")";
    }

    public static void main(String[] args) {
        try {
            DetectOS();
            Configuration configuration = new Configuration();

            fStacktrace = ArgParser.RegisterFlag("Prints a stacktrace if errors occur during the execution of the built tool", "-stacktrace");
            fExtractAll = ArgParser.RegisterFlag("Unpacks the complete dependency archives. By default only .sp and .inc are extracted", "x","-fulldeps");
            fOffline = ArgParser.RegisterFlag("Offline mode does not try to resolve any dependencies for faster compile times.", "-offline");
            fNoExec = ArgParser.RegisterFlag("Skip exec tasks. In case the build environment want's to be extra safe.", "e", "-no-exec");
            fInteractive = ArgParser.RegisterFlag("Start interactive single mode, reads instruction from StdIn and runs it.", "i");
            fInteractiveBatch = ArgParser.RegisterFlag("Start interactive batch mode, best for piping scripts through StdIn.", "I");
            fNoScripts = ArgParser.RegisterFlag("Disable script executions for embedded scripts", "s", "-no-script");
            fCacheDir = ArgParser.RegisterValueFlag("Where to put the .spcache directory. Relative directories are relative to pwd/cd.", "-cachedir");
            ArgParser.usageString = "<Args> [--] [BuildFile]";
            ArgParser.description = "SPSauce is a build tool that's primarily intended to fetch dependencies from SM sources including the forums";
            ArgParser.Parse(args);

            if (ArgParser.GetStringArgs().isEmpty()) {
                selfScript = Paths.get(".", "sp.sauce").toAbsolutePath().normalize();
            } else {
                selfScript = Paths.get(ArgParser.GetStringArgs().get(0)).toAbsolutePath().normalize();
            }
            scriptdir = selfScript.getParent();
            execdir = Paths.get(".").toAbsolutePath().normalize();
            if (ArgParser.IsFlagSet(fCacheDir)) {
                try {
                    cachedir = Paths.get(ArgParser.GetFlagValue(fCacheDir));
                } catch (InvalidPathException ignore) { }
            } else if (configuration.cacheDirectory.isAbsolute()) {
                cachedir = configuration.cacheDirectory.normalize();
            } else {
                cachedir = execdir.resolve(configuration.cacheDirectory).normalize();
            }

            if (ArgParser.IsFlagSet(fInteractive) || ArgParser.IsFlagSet(fInteractiveBatch)) {
                interactiveHandler();
            } else {
                if (!Files.isReadable(selfScript)) throw new IOException("Could not read script file");
                System.out.println("> Parsing " + selfScript.getFileName() + "...");
                BuildScript script = new BuildScript(selfScript);
                System.out.println("> Running build script");
                script.run();
                System.out.println("> Build successful");
            }

        } catch (Throwable e) {
            if (ArgParser.IsFlagSet(fStacktrace)) e.printStackTrace();
            else System.err.println(e.getClass().getSimpleName()+": "+e.getMessage());
            System.exit(1);
        } finally {
            exec.shutdownNow();
        }
    }

    private static void interactiveHandler() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        boolean batching = ArgParser.IsFlagSet(fInteractiveBatch);
        boolean batchStart = batching;
        boolean hadErrors = false;
        StringBuilder sb = new StringBuilder();
        String line;

        System.out.println("Interactive Mode - Use exit or quit to exit;");
        System.out.println("Use batch to collect instruction and run to execute the batch.");
        if (batchStart) System.out.println("Starting in batched mode: run is optional if StdIn ends");
        System.out.println();

        do { // foot controlled because we want to react to the end of input
            line = br.readLine();
            if (line != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
            }

            boolean singleRun = (!batching && line != null);
            boolean batchRun = (batching && ("run".equalsIgnoreCase(line) || (line == null && batchStart)));
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) break;
            else if ("batch".equalsIgnoreCase(line)) {
                if (batching) System.out.println("> Already in batch mode");
                else {
                    System.out.println("> Batching until `run`");
                    batching = true;
                }
            }
            else if (singleRun || batchRun) {
                //outside batching we run every line, otherwise we execute the batch on "run"
                // if we started in batch mode and the input ends we can assume the pipe that fed us is done. otherwise it was probably ^C
                try (InputStream in = IOUtils.toInputStream(batchRun ? sb : line, "UTF-8")) {
                    BuildScript script = new BuildScript(in);
                    if (batchRun) System.out.println("> Running build script");
                    script.run();
                    System.out.println("> OK");
                } catch (UnknownInstructionException wha) {
                    System.err.println(wha.getMessage());
                    System.err.flush(); //guarantee that "> Error" is the last thing in terminal
                    System.out.println("> Error");
                    hadErrors = true;
                } catch (Throwable e) {
                    if (ArgParser.IsFlagSet(fStacktrace)) e.printStackTrace();
                    else System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
                    System.err.flush(); //guarantee that "> Error" is the last thing in terminal
                    System.out.println("> Error");
                    hadErrors = true;
                } finally {
                    sb.setLength(0); //reset
                    batching = false;
                }
            } else if (line != null) {
                sb.append(line).append("\n");
            }
        } while (line != null);
        //java is weird and won't terminate, even if all stack frames are gone after this method, so we just force exit
        System.exit(hadErrors ? 1 : 0);
    }

    private static void DetectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) OS = OperatingSystem.Windows;
        else if (os.contains("mac")) OS = OperatingSystem.Mac;
        else if (os.contains("nix")||os.contains("nux")||os.contains("aix")) OS = OperatingSystem.Linux;

        if (OS == OperatingSystem.Windows) {
            String env = System.getenv("PROCESSOR_ARCHITECTURE");
            if (env == null || !env.endsWith("64")) {
                // probably x86, check for 32 bit process on 64 bit machine
                env = System.getenv("PROCESSOR_ARCHITEW6432");
            }
            if (env == null)
                System.err.println("Could not read PROCESSOR_ARCHITECTURE");
            ARCH64 = (env != null && env.endsWith("64"));
        } else if (OS == OperatingSystem.Linux) {
            try {
                Process proc = Runtime.getRuntime().exec("getconf LONG_BIT");
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                ARCH64 = "64".equals(br.readLine());
                proc.waitFor();
            } catch (Throwable e) {
                System.err.println("Could not read getconf LONG_BIT");
                ARCH64 = false;
            }
        } else {
            ARCH64 = true; //idk man
        }

        System.out.println("OS Check: Running on " + OS.name() + " " + (ARCH64?"64":"32") + "-bit");
    }

}
