package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.ExecTask;
import com.dosmike.spsauce.utils.Strings;

public class ActionExec implements ScriptAction {

    String commandLine;
    BuildScript context;
    public ActionExec(BuildScript context, String args) {
        this.context = context;
        this.commandLine = args;
    }

    @Override
    public void run() throws Throwable {
        String[] args = Strings.translateCommandline(commandLine);
        context.taskList.and(new ExecTask(args));
    }

}
