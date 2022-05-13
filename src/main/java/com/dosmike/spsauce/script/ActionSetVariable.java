package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.tasks.SearchValueInFileTask;
import com.dosmike.spsauce.utils.Nullable;
import com.dosmike.spsauce.utils.Ref;

import java.util.regex.Pattern;

public class ActionSetVariable implements ScriptAction {

    BuildScript context;
    Task task;

    public ActionSetVariable(BuildScript context, String argstring) {
        Ref<String> args = Ref.of(argstring);
        this.context = context;
        String variable;
        String target;
        Pattern search;
        String value;

        variable = cleanArg(breakArgs(args));
        if (variable == null || variable.isEmpty())
            throw new IllegalArgumentException("Not enough arguments, variable expected!");
        if (!variable.matches("^[$%]\\{\\w+}"))
            throw new IllegalArgumentException("Invalid variable name '"+variable+"'");

        String kwd = breakArgs(args);
        if (kwd == null)
            throw new IllegalArgumentException("Unexpected end of line, 'as' or 'from' expected!");

        if ("to".equalsIgnoreCase(kwd)) {
            //"literal" value
            final String bound = args.it.trim();
            task = ()->BuildScript.setVariable(variable, BuildScript.injectRefs(bound));
        } else {
            //value from file
            if ("as".equalsIgnoreCase(kwd)) {
                value = cleanArg(breakArgs(args));
                if (value == null)
                    throw new IllegalArgumentException("Unexpected end of line, format expected!");
                kwd = breakArgs(args);
                if (kwd == null)
                    throw new IllegalArgumentException("Unexpected end of line, 'from' expected!");
            } else value = "\\0";

            if (!"from".equalsIgnoreCase(kwd))
                throw new IllegalArgumentException("Illegal keyword '" + kwd + "'!");
            target = cleanArg(breakArgs(args));
            if (target == null)
                throw new IllegalArgumentException("Unexpected end of line, file expected!");
            String pattern = cleanArg(breakArgs(args));
            if (pattern == null)
                throw new IllegalArgumentException("Unexpected end of line, regex pattern expected!");
            search = Pattern.compile(pattern, Pattern.MULTILINE);

            task = new SearchValueInFileTask(variable, target, search, value);
        }
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(task);
    }

    private String cleanArg(@Nullable String arg) {
        if (arg == null) return arg;
        else if (arg.charAt(0)=='"') {
            if (arg.length() == 2) return "";
            else return arg.substring(1, arg.length()-1).replace("\"\"","\"");
        } else {
            return arg;
        }
    }
    //handle args bash like as i don't care for other terminators and it makes regex patterns easier to write
    private String breakArgs(Ref<String> args) {
        String work = args.it;
        //ltrim
        int from=0;
        while (from < work.length() && work.charAt(from)==' ') from++;
        if (from >= work.length()) {
            args.it = "";
            return null; //nothing remained, all spaces
        }
        int start=from;
        //get "word"
        if (work.charAt(start)=='"') {
            from++;
            int end = -1;
            int fwd;
            while ((fwd = work.indexOf('"', from))>0) {
                if (fwd+1<work.length() && work.charAt(fwd+1)=='"') {
                    //escaped quote: "hello "" world"
                    from = fwd+2; //continue after ""
                } else {
                    end = fwd+1;
                    break;
                }
            }
            if (end < 0) throw new IllegalArgumentException("Unterminated String, \" expected!");

            if (end >= work.length()) args.it = ""; //if nothing remains, set empty
            else args.it = work.substring(end); // otherwise remove the stuff we will return
            return work.substring(start,end); //return the substring / word / quote
        } else {
            int fwd = work.indexOf(' ', from);
            if (fwd < 0) { //no more spaces in the string
                args.it = "";
                return work.substring(from);
            } else {
                args.it = work.substring(fwd);
                return work.substring(from,fwd);
            }
        }
    }

}
