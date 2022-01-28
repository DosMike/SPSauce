package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.utils.BaseIO;

import java.nio.file.Paths;

public class ActionMkdir implements ScriptAction {

    String directory;
    BuildScript context;

    public ActionMkdir(BuildScript context, String dir) {
        this.context = context;
        this.directory = dir;
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(()-> BaseIO.MakeDirectories(Executable.workdir, Paths.get(directory)) );
    }
}
