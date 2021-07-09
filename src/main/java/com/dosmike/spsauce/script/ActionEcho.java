package com.dosmike.spsauce.script;

public class ActionEcho implements ScriptAction {

    String message;
    boolean die;

    public ActionEcho(String message, boolean terminate) {
        this.message = message;
        this.die = terminate;
    }

    @Override
    public void run() throws Throwable {
        System.out.println(BuildScript.injectRefs(message));
        if (die) System.exit(1);
    }
}
