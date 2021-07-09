package com.dosmike.spsauce.script;

import com.dosmike.spsauce.utils.InOut;

import java.nio.file.Paths;

public class ActionDelete implements ScriptAction {

    BuildScript context;
    String target;

    public ActionDelete(BuildScript context, String location) {
        this.context = context;
        this.target = location;
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(()-> InOut.RemoveRecursive(Paths.get(target)) );
    }
}
