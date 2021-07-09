package com.dosmike.spsauce.script;

import com.dosmike.spsauce.utils.InOut;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ActionMove implements ScriptAction {

    Path from, to;
    BuildScript context;

    public ActionMove(BuildScript context, String location) {
        this.context = context;
        String[] tmp = location.split(":");
        this.from = Paths.get(tmp[0].trim()).toAbsolutePath().normalize();
        this.to = Paths.get(tmp[1].trim()).toAbsolutePath().normalize();
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(()-> InOut.MoveFiles(from,to) );
    }
}
