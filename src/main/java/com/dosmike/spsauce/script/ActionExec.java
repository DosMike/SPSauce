package com.dosmike.spsauce.script;

import com.dosmike.spsauce.tasks.ExecTask;

import java.util.ArrayList;

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
        //collect from " to ", from ' to ' or until [ ]
        //apache version does not check for quote escapes "..."'"'"..." for linux nor ^" for windows so i wont either
        ArrayList<String> args = new ArrayList<>();
        int from=0;
        if (line != null && !line.isEmpty()) while (from < line.length()) {
            //skip space
            while (from < line.length() && line.charAt(from) == ' ') from++;
            if (from >= line.length()) break; //end of string
            //get next argument, quoted or not from first char
            switch (line.charAt(from)) {
                case '\'': {
                    int end = line.indexOf('\'',from+1);
                    if (end < 0) throw new RuntimeException("Unbalanced single quote starting at "+from);
                    args.add(line.substring(from,end));
                    from = end+1;
                    break;
                }
                case '"': {
                    int end = line.indexOf('"', from+1);
                    if (end < 0) throw new RuntimeException("Unbalanced double quote starting at "+from);
                    args.add(line.substring(from,end));
                    from = end+1;
                    break;
                }
                default: {
                    int end = line.indexOf(' ', from+1);
                    String substring = (end < 0) ? line.substring(from) : line.substring(from, end);
                    args.add(substring);
                    from += substring.length();
                    break;
                }
            }
        }
        return args.toArray(new String[0]);
    }
}
