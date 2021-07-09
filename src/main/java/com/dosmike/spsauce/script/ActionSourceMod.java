package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.SourceModTask;

public class ActionSourceMod implements ScriptAction {

    BuildScript context;
    String version;
    String build;
    ActionSourceMod(BuildScript context, String ver, String build) {
        this.context = context;
        this.version = ver;
        this.build = build;
        if (this.build == null || this.build.isEmpty()) this.build = "latest";
    }

    @Override
    public void run() throws Throwable {
        if (context.lock.Find("sourcemod")!=null) return;
        context.taskList.and(new SourceModTask(context.lock, version, build));
    }
}
