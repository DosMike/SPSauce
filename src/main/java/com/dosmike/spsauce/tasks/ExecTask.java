package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.BuildScript;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ExecTask implements Task {

    List<String> args;
    public ExecTask(String[] args) {
        this.args = Arrays.asList(args);
    }

    @Override
    public void run() throws Throwable {
        ProcessBuilder pb = new ProcessBuilder(args.stream().map(BuildScript::injectRefs).collect(Collectors.toList()));
        pb.directory(Executable.workdir.toFile());
        BuildScript.applyEnvironment(pb);
        pb.inheritIO();
        Process process = pb.start();
        if (process.waitFor()!=0) throw new RuntimeException("Execution failed!");
    }

}
