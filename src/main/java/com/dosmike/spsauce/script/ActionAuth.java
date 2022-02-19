package com.dosmike.spsauce.script;

import com.dosmike.spsauce.github.HubAuthorization;

import java.util.Arrays;

public class ActionAuth implements ScriptAction {

    ScriptAction actual;
    boolean dontFail;

    ActionAuth(BuildScript context, String provider, String[] args) {
        if (provider.equalsIgnoreCase("try")) {
            if (args.length == 0) throw new RuntimeException("No authorization specified");
            dontFail = true;
            provider = args[0];
            args = Arrays.copyOfRange(args, 1, args.length);
        } else dontFail = false;

        if (provider.equalsIgnoreCase("github")) {
            if (args.length < 1 || args[0].isEmpty()) throw new RuntimeException("Not enough arguments for `auth github`: <token> [login] expected");
            else if (args.length > 2) throw new RuntimeException("Too many arguments for `auth github`: <token> [login] expected");
            actual = new GitHub(context, args[0], args.length>1?args[1]:null);
        } else {
            throw new RuntimeException("Authorization not supported for `auth "+provider+"`");
        }
    }

    @Override
    public void run() throws Throwable {
        if (dontFail) {
            try {
                actual.run();
            } catch (Throwable t) {
                System.out.println("Authorization failed for "+actual.getClass().getSimpleName()+": "+t.getMessage());
            }
        } else {
            actual.run();
        }
    }

    private static class GitHub implements ScriptAction {
        String pat, login=null;
        BuildScript ctx;
        public GitHub(BuildScript context, String pat, String login) {
            this.pat = BuildScript.injectRefs(pat);
            if (login != null) this.login = BuildScript.injectRefs(login);
            this.ctx = context;
        }
        @Override
        public void run() throws Throwable {
            System.out.println("Authenticating GitHub");
            ctx.authorizationMap.put("github", new HubAuthorization(pat, login));
        }
    }
}
