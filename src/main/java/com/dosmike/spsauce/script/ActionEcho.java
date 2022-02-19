package com.dosmike.spsauce.script;

import com.dosmike.spsauce.utils.Nullable;

public class ActionEcho implements ScriptAction {

    String message;
    boolean die;
    BuildScript context;

    public ActionEcho(@Nullable BuildScript context, String message, boolean terminate) {
        this.message = message;
        this.die = terminate;
    }

    @Override
    public void run() throws Throwable {
        if (context != null) {
            context.taskList.and(()->{
                System.out.println(BuildScript.injectRefs(message));
                if (die) System.exit(1);
            });
        } else {
            System.out.println(BuildScript.injectRefs(message));
            if (die) System.exit(1);
        }
    }
}
