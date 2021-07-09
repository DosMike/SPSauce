package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.CompileTask;

public class ActionSpcomp implements ScriptAction {

    String commandLine;
    BuildScript context;
    public ActionSpcomp(BuildScript context, String args) {
        this.context = context;
        this.commandLine = args;
    }

    @Override
    public void run() throws Throwable {
        String[] args = ActionExec.translateCommandline(commandLine);
        for (int i=0;i<args.length;i++) args[i]=BuildScript.injectRefs(args[i]);
        context.taskList.and(new CompileTask(args));
    }
}
