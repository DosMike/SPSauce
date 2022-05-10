package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Authorization;
import com.dosmike.spsauce.am.AMAuthorization;
import com.dosmike.spsauce.github.HubAuthorization;

import java.util.Arrays;
import java.util.Locale;

public class ActionAuth implements ScriptAction {

    interface AuthFactory {
        Authorization auth() throws Throwable;
        String name();
    }

    AuthFactory factory;
    boolean dotry;

    ActionAuth(BuildScript context, String provider, String[] args) {
        if (provider.equalsIgnoreCase("try")) {
            if (args.length == 0) throw new RuntimeException("No authorization specified");
            dotry = true;
            provider = args[0];
            args = Arrays.copyOfRange(args, 1, args.length);
        } else dotry = false;

        if (provider.equalsIgnoreCase("github")) {
            if (args.length < 1 || args[0].isEmpty())
                throw new RuntimeException("Not enough arguments for `auth github`: <token> [login] expected");
            else if (args.length > 2)
                throw new RuntimeException("Too many arguments for `auth github`: <token> [login] expected");
            factory = new GitHub(context, args[0], args.length > 1 ? args[1] : null);
        } else if (provider.toLowerCase(Locale.ROOT).matches("forums?") || provider.equalsIgnoreCase("am")) {
            if (args.length < 2 || args[0].isEmpty() || args[1].isEmpty())
                throw new RuntimeException("Not enough arguments for `auth forum`: <user> <password>");
            else if (args.length > 2)
                throw new RuntimeException("Too many aruments for `auth forum`: <user> <password>");
            factory = new Forums(context, args[0], args[1]);
        } else {
            throw new RuntimeException("Authorization not supported for `auth "+provider+"`");
        }
    }

    @Override
    public void run() throws Throwable {
        boolean wasAuthed = BuildScript.authorizationMap.containsKey(factory.name().toLowerCase());
        System.out.print("Authenticating "+factory.name()+"... ");
        try {
            BuildScript.authorizationMap.put(factory.name().toLowerCase(), factory.auth());
            if (wasAuthed)
                System.out.print("Updated!");
            else
                System.out.print("Successful!");
        } catch (Throwable t) {
            if (!dotry)
                throw t;
            else if (wasAuthed)
                System.out.print("Failed - Retaining previous: "+t.getMessage());
            else
                System.out.print("Failed: "+t.getMessage());
        } finally {
            System.out.println();
        }
    }

    private static class GitHub implements AuthFactory {
        String pat, login=null;
        BuildScript ctx;
        public GitHub(BuildScript context, String pat, String login) {
            this.pat = BuildScript.injectRefs(pat);
            if (login != null) this.login = BuildScript.injectRefs(login);
            this.ctx = context;
        }
        @Override
        public Authorization auth() throws Throwable {
            return new HubAuthorization(pat, login);
        }

        @Override
        public String name() {
            return "GitHub";
        }
    }
    private static class Forums implements AuthFactory {
        String name,pass;
        BuildScript ctx;
        public Forums(BuildScript context, String user, String password) {
            this.name = BuildScript.injectRefs(user);
            this.pass = BuildScript.injectRefs(password);
            this.ctx = context;
        }

        @Override
        public Authorization auth() throws Throwable {
            return new AMAuthorization(name, pass);
        }

        @Override
        public String name() {
            return "AlliedMods";
        }
    }
}
