package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.tasks.CloneTask;
import com.dosmike.spsauce.tasks.ExecTask;
import org.apache.commons.exec.CommandLine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ActionClone implements ScriptAction {

    String source, branch, dest;
    BuildScript context;
    public ActionClone(BuildScript context, String args) {
        this.context = context;
        int at = args.toLowerCase().indexOf(" into ");
        this.dest = args.substring(at+6);
        String left = args.substring(0,at);
        if ((at=left.lastIndexOf(' '))>=0) {
            this.source = left.substring(0,at);
            this.branch = left.substring(at+1);
        } else {
            this.source = left;
            this.branch = null;
        }
        if (!(this.source.startsWith("https://") || this.source.startsWith("git@")) || !this.source.endsWith(".git") || this.source.contains(" "))
            throw new IllegalArgumentException("Clone url seems invalid");
    }

    @Override
    public void run() throws Throwable {
        Path path = Paths.get(BuildScript.injectRefs(dest)).toAbsolutePath().normalize();
        if (!path.startsWith(Executable.workdir))
            throw new RuntimeException("You can only clone into subdirectories!");
        context.taskList.and(new CloneTask(source,Executable.workdir.relativize(path),BuildScript.injectRefs(branch)));
    }
}
