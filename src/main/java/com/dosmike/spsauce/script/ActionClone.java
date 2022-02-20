package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.tasks.CloneTask;

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
        context.taskList.and(new CloneTask(source,dest,branch));
    }
}
