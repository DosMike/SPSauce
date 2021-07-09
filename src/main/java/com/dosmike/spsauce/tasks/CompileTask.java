package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class CompileTask implements Task {

    Path compilerPath;
    List<String> args;
    public CompileTask(String[] args) {
        Path baseDir = Executable.workdir.resolve(Paths.get("spcache", "addons", "sourcemod", "scripting"));
        if (Executable.OS == Executable.OperatingSystem.Windows)
            compilerPath = baseDir.resolve("spcomp.exe");
        else if (Executable.OS == Executable.OperatingSystem.Linux || Executable.OS == Executable.OperatingSystem.Mac)
            compilerPath = baseDir.resolve("spcomp");
        this.args = new LinkedList<>();
        this.args.add(compilerPath.toString());
        this.args.add("-i"+ cwdRelative(baseDir.resolve("include")));
        Path include;
        if (Files.isDirectory(include = Executable.workdir.resolve(Paths.get("include"))))
            this.args.add("-i"+ cwdRelative(include));
        if (Files.isDirectory(include = Executable.workdir.resolve(Paths.get("scripting","include"))))
            this.args.add("-i"+ cwdRelative(include));
        if (Files.isDirectory(include = Executable.workdir.resolve(Paths.get("addons","sourcemod","scripting","include"))))
            this.args.add("-i"+ cwdRelative(include));
        this.args.addAll(Arrays.asList(args));
    }

    private Path cwdRelative(Path p) {
        try {
            return Executable.workdir.relativize(p);
        } catch (Throwable x) {
            return p;
        }
    }

    @Override
    public void run() throws Throwable {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(Executable.workdir.toFile());
        pb.inheritIO();
        Process process = pb.start();
        if (process.waitFor()!=0) throw new RuntimeException("Compilation failed!");
    }

}
