package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.ScriptTask;

import java.util.List;

/**
 * Purpose of subscripts is not to do any heavy lifting! They are meant to do complex variable processing
 * e.g. Convert version data pulled through regex and set a result variable
 */
public class ActionSubscript implements ScriptAction {

    final private BuildScript context;
    final private String code;

    public ActionSubscript(BuildScript context, int parseMode, List<String> code) {
        this.context = context;
        if (parseMode != 2) throw new RuntimeException("Unsupported script type");
        this.code = String.join("\n", code);
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(new ScriptTask.Lua(code));
    }

}
