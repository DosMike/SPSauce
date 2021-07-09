package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.ExecTask;
import org.apache.commons.exec.CommandLine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ActionExec implements ScriptAction {

    String commandLine;
    BuildScript context;
    public ActionExec(BuildScript context, String args) {
        this.context = context;
        this.commandLine = args;
    }

    @Override
    public void run() throws Throwable {
        String[] args = translateCommandline(commandLine);
        for (int i=0;i<args.length;i++) args[i]=BuildScript.injectRefs(args[i]);
        context.taskList.and(new ExecTask(args));
    }

    static String[] translateCommandline(String line) {
        try {
            Method m = CommandLine.class.getDeclaredMethod("translateCommandline", String.class);
            m.setAccessible(true);
            return (String[])m.invoke(null, line);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
