package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.utils.BaseIO;

import java.nio.file.Path;
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
        context.taskList.and(()-> {
            Path fullPath = BaseIO.MakePathAbsoluteIfLegal(BuildScript.injectRefs(directory));
            if (fullPath.startsWith(Executable.execdir))
                BaseIO.MakeDirectories(Executable.execdir, Executable.execdir.relativize(fullPath));
            else if (fullPath.startsWith(Executable.cachedir))
                BaseIO.MakeDirectories(Executable.cachedir, Executable.cachedir.relativize(fullPath));
            else
                throw new RuntimeException("Illegal directory - this should have already been caught by BaseIO.MakePathAbsoluteIfLegal");
        } );
    }
}
