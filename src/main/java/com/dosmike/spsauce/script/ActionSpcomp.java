package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.CompileTask;
import com.dosmike.spsauce.utils.Strings;

public class ActionSpcomp implements ScriptAction {

    String commandLine;
    BuildScript context;
    public ActionSpcomp(BuildScript context, String args) {
        this.context = context;
        this.commandLine = args;
    }

    @Override
    public void run() throws Throwable {
        String[] args = Strings.translateCommandline(commandLine);
        context.taskList.and(new CompileTask(args));
    }
}
