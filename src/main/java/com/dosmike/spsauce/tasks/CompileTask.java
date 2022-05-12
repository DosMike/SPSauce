package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.utils.BaseIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class CompileTask implements Task {

    Path compilerPath;
    List<String> args, userArgs;
    public CompileTask(String[] args) {
        Path baseDir = Executable.workdir.resolve(Paths.get("spcache", "addons", "sourcemod", "scripting"));
        if (Executable.OS == Executable.OperatingSystem.Windows)
            compilerPath = baseDir.resolve("spcomp.exe");
        else if (Executable.OS == Executable.OperatingSystem.Linux && Executable.ARCH64)
            compilerPath = baseDir.resolve("spcomp64");
        else
            compilerPath = baseDir.resolve("spcomp");
        this.args = new LinkedList<>();
        this.args.add(compilerPath.toString());

        Path include;
        if (Files.isDirectory(include = Executable.workdir.resolve(Paths.get("include"))))
            this.args.add("-i"+ cwdRelative(include));
        if (Files.isDirectory(include = Executable.workdir.resolve(Paths.get("scripting","include"))))
            this.args.add("-i"+ cwdRelative(include));
        if (Files.isDirectory(include = Executable.workdir.resolve(Paths.get("addons","sourcemod","scripting","include"))))
            this.args.add("-i"+ cwdRelative(include));
        this.userArgs = Arrays.asList(args);
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
        ArrayList<String> cmd = new ArrayList<>(args);
        cmd.addAll(userArgs.stream().map(BuildScript::injectRefs).collect(Collectors.toList()));
        BaseIO.MakeExecutable(Paths.get(cmd.get(0)));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Executable.workdir.toFile());
        pb.inheritIO();
        Process process = pb.start();
        if (process.waitFor()!=0) throw new RuntimeException("Compilation failed!");
    }

}
