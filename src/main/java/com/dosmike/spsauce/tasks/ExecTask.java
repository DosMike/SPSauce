package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.BuildScript;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ExecTask implements Task {

    Path compilerPath;
    List<String> args;
    public ExecTask(String[] args) {
        this.args = new LinkedList<>();
        this.args.addAll(Arrays.asList(args));
    }

    @Override
    public void run() throws Throwable {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(Executable.workdir.toFile());
        BuildScript.applyEnvironment(pb);
        pb.inheritIO();
        Process process = pb.start();
        if (process.waitFor()!=0) throw new RuntimeException("Execution failed!");
    }

}
