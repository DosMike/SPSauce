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
        Path target = Paths.get(BuildScript.injectRefs(path)).toAbsolutePath().normalize();
        if (!target.startsWith(Executable.workdir))
            throw new RuntimeException("You can only clone into subdirectories!");
        target = Executable.workdir.relativize(target);
        String checkout = BuildScript.injectRefs(branch);


        System.out.println("Invoking GIT for "+target);
        Path clonebase = Executable.workdir.resolve("spcache");
        Path cloneTarget = clonebase.resolve(target);
        if (!Files.isDirectory(cloneTarget)) {
            BaseIO.MakeDirectories(clonebase, target);
        } else {
            System.out.println("  Target directory exists, assuming cloned");
            return;
        }
        call(cloneTarget, "git", "init");
        call(cloneTarget, "git", "remote", "add", "origin", gituri);
        call(cloneTarget, "git", "fetch", "origin");
        if (checkout == null) {
            // this is a little hack that'll fetch the main branch's head detached
            call(cloneTarget, "git", "remote", "set-head", "origin", "--auto");
            call(cloneTarget, "git", "checkout", "origin");
        } else
            call(cloneTarget, "git", "checkout", checkout);
        call(cloneTarget, "git", "submodule", "update", "--init", "--recursive");
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
