package com.dosmike.spsauce.script;

import com.dosmike.spsauce.utils.InOut;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionMove implements ScriptAction {

    Path from, to;
    BuildScript context;

    InOut.ReplaceFlag replace;
    boolean copy;

    private static final Pattern replaceRule = Pattern.compile("\\s+replace\\s+(\\w+)$");

    public ActionMove(BuildScript context, String location, boolean copy) {
        this.context = context;
        this.copy = copy;
        Matcher matcher = replaceRule.matcher(location);
        if (matcher.find()) {
            String replaceType = matcher.group(1);
            if ("all".equalsIgnoreCase(replaceType) || "any".equalsIgnoreCase(replaceType)) {
                replace = InOut.ReplaceFlag.All;
            } else if ("older".equalsIgnoreCase(replaceType)) {
                replace = InOut.ReplaceFlag.Older;
            } else if ("skip".equalsIgnoreCase(replaceType) || "none".equalsIgnoreCase(replaceType)) {
                replace = InOut.ReplaceFlag.Skip;
            } else if ("error".equalsIgnoreCase(replaceType) || "fail".equalsIgnoreCase(replaceType)) {
                replace = InOut.ReplaceFlag.Error;
            } else {
                throw new RuntimeException("Unknown replace condition: "+replaceType);
            }
            //strip replace filter from the input
            location = location.substring(0,matcher.start());
        } else replace = InOut.ReplaceFlag.Error;

        String[] tmp = location.split(":");
        this.from = Paths.get(tmp[0].trim()).toAbsolutePath().normalize();
        this.to = Paths.get(tmp[1].trim()).toAbsolutePath().normalize();
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(()-> InOut.MoveFiles(from,to,!copy,replace) );
    }
}
