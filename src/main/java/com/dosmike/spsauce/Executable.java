package com.dosmike.spsauce;

import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.utils.ArgParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Executable {

    public enum OperatingSystem {
        Windows,
        Linux,
        Mac,
        Unsupported,
    }

    public static Path workdir;
    public static OperatingSystem OS = OperatingSystem.Unsupported;
    public static ExecutorService exec = Executors.newCachedThreadPool();
    public static boolean ARCH64;
    public static Path selfScript;
    private static ArgParser.Flag fStacktrace;
    public static ArgParser.Flag fExtractAll;
    public static ArgParser.Flag fOffline;
    public static ArgParser.Flag fNoExec;

    public static void main(String[] args) {
        try {

            fStacktrace = ArgParser.RegisterFlag("Prints a stacktrace if errors occur during the execution of the built tool", "-stacktrace");
            fExtractAll = ArgParser.RegisterFlag("Unpacks the complete dependency archives. By default only .sp and .inc are extracted", "x","-fulldeps");
            fOffline = ArgParser.RegisterFlag("Offline mode does not try to resolve any dependencies for faster compile times.", "-offline");
            fNoExec = ArgParser.RegisterFlag("Skip exec tasks. In case the build environment want's to be extra safe.", "s", "-no-exec");
            ArgParser.usageString = "<Args> [--] [BuildFile]";
            ArgParser.description = "SPSauce is a build tool that's primarily intended to fetch dependencies from SM sources including the forums";
            ArgParser.Parse(args);
            if (ArgParser.GetStringArgs().isEmpty()) {
                selfScript = Paths.get(".", "sp.sauce").toAbsolutePath().normalize();
                workdir = selfScript.getParent();
            } else {
                selfScript = Paths.get(ArgParser.GetStringArgs().get(0)).toAbsolutePath().normalize();
                if (Files.isReadable(selfScript)) throw new IOException("Could not read script file");
                workdir = selfScript.getParent();
            }
            DetectOS();

            System.out.println("> Parsing "+selfScript.getFileName()+"...");
            BuildScript bs = new BuildScript(selfScript);
            System.out.println("> Running build script");
            bs.run();
            System.out.println("> Build successful");

        } catch (Throwable e) {
            if (ArgParser.IsFlagSet(fStacktrace)) e.printStackTrace();
            else System.err.println(e.getClass().getSimpleName()+": "+e.getMessage());
            System.exit(1);
        } finally {
            exec.shutdownNow();
        }
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
