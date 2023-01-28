package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.utils.BaseIO;
import com.dosmike.spsauce.utils.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CloneTask implements Task {

    String gituri, branch;
    String path;

    public CloneTask(String fromURI, String targetPath, @Nullable String branch) {
        this.gituri = fromURI;
        this.path = targetPath;
        this.branch = branch;
    }

    @Override
    public void run() throws Throwable {
        //late process of ref injections
        Path target;
        try {
            target = BaseIO.MakePathAbsoluteIfLegal(BuildScript.injectRefs(path));
        } catch (IllegalArgumentException exc) {
            throw new RuntimeException("You can only clone into subdirectories!");
        }
        String checkout = BuildScript.injectRefs(branch);


        System.out.println("Invoking GIT for "+target);
        if (!Files.isDirectory(target)) {
            BaseIO.MakeDirectories(target, Paths.get("."));
        } else {
            System.out.println("  Target directory exists, assuming cloned");
            return;
        }
        call(target, "git", "init");
        call(target, "git", "remote", "add", "origin", gituri);
        call(target, "git", "fetch", "origin");
        if (checkout == null) {
            // this is a little hack that'll fetch the main branch's head detached
            call(target, "git", "remote", "set-head", "origin", "--auto");
            call(target, "git", "checkout", "origin");
        } else
            call(target, "git", "checkout", checkout);
        call(target, "git", "submodule", "update", "--init", "--recursive");
        //remove git meta
//        InOut.RemoveRecursive(cloneTarget.resolve(".git"));
    }

    private void call(Path cwd, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        BuildScript.applyEnvironment(pb);
        pb.inheritIO();
        Process process = pb.start();
        if (process.waitFor()!=0) throw new RuntimeException("Execution failed!");
    }

}
