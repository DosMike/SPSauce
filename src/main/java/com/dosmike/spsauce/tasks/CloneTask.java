package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.utils.InOut;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class CloneTask implements Task {

    String gituri, branch;
    Path target;

    public CloneTask(String fromURI, Path targetPath, @Nullable String branch) {
        this.gituri = fromURI;
        this.target = targetPath;
        this.branch = branch;
    }

    @Override
    public void run() throws Throwable {
        System.out.println("Invoking GIT for "+target);
        Path clonebase = Executable.workdir.resolve("spcache");
        Path cloneTarget = clonebase.resolve(target);
        if (!Files.isDirectory(cloneTarget)) {
            InOut.MakeDirectories(clonebase, target);
        } else {
            System.out.println("  Target directory exists, assuming cloned");
            return;
        }
        call(cloneTarget, "git", "init");
        call(cloneTarget, "git", "remote", "add", "origin", gituri);
        call(cloneTarget, "git", "fetch", "origin");
        if (branch == null) {
            // this is a little hack that'll fetch the main branch's head detached
            call(cloneTarget, "git", "remote", "set-head", "origin", "--auto");
            call(cloneTarget, "git", "checkout", "origin");
        } else
            call(cloneTarget, "git", "checkout", branch);
        call(cloneTarget, "git", "submodule", "update", "--init", "--recursive");
        //remove git meta
//        InOut.RemoveRecursive(cloneTarget.resolve(".git"));
    }

    private void call(Path cwd, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.inheritIO();
        Process process = pb.start();
        if (process.waitFor()!=0) throw new RuntimeException("Execution failed!");
    }

}
