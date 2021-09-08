package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Authorization;
import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.TaskList;
import com.dosmike.spsauce.utils.ArgParser;
import com.dosmike.spsauce.utils.Ref;
import com.dosmike.spsauce.utils.UnknownInstructionException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildScript {

    TaskList taskList = new TaskList();
    static Map<String, Authorization> authorizationMap = new HashMap<>(); //persis for interactive mode
    PluginLock lock;

    List<ScriptAction> parsed = new LinkedList<>();

    public BuildScript(Path fromFile) throws IOException {
        this(Files.newInputStream(fromFile));
    }

    public BuildScript(InputStream inputStream) throws IOException {
        lock = new PluginLock();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            Ref<String> line=new Ref<>(), word=new Ref<>();
            while ((line.it =br.readLine())!=null) {
                line.it = line.it.trim();
                if (line.it.startsWith("//") || line.it.startsWith("#") || line.it.isEmpty()) continue;

                getWord(line, word);
                if (word.it.startsWith("@")) {
                    if (word.it.equalsIgnoreCase("@windows")) { if (!Executable.OS.equals(Executable.OperatingSystem.Windows)) continue; }
                    else if (word.it.equalsIgnoreCase("@linux")) { if (!Executable.OS.equals(Executable.OperatingSystem.Linux)) continue; }
                    else if (word.it.equalsIgnoreCase("@mac")) { if (!Executable.OS.equals(Executable.OperatingSystem.Mac)) continue; }
                    else throw new UnknownInstructionException("Invalid OS filter: "+word.it);
                    getWord(line, word);
                }
                if (word.it.equalsIgnoreCase("auth")) {
                    getWord(line, word);
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        parsed.add(new ActionAuth(this, word.it, line.it.split(" ")));
                } else if (word.it.equalsIgnoreCase("sourcemod")) {
                    getWord(line, word);
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        parsed.add(new ActionSourceMod(this, word.it, line.it));
                } else if (word.it.equalsIgnoreCase("dependency")) {
                    getWord(line, word);
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        parsed.add(new ActionDependency(this, word.it, line.it));
                } else if (word.it.equalsIgnoreCase("clone")) {
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        parsed.add(new ActionClone(this, line.it));
                } else if (word.it.equalsIgnoreCase("compilepool")) {
                    TaskList.setCompilePoolSize(Integer.parseInt(line.it));
                } else if (word.it.equalsIgnoreCase("compile")||word.it.equalsIgnoreCase("spcomp")) {
                    parsed.add(new ActionSpcomp(this, line.it));
                } else if (word.it.equalsIgnoreCase("exec")) {
                    if (!ArgParser.IsFlagSet(Executable.fNoExec))
                        parsed.add(new ActionExec(this, line.it));
                } else if (word.it.equalsIgnoreCase("echo")) {
                    parsed.add(new ActionEcho(line.it,false));
                } else if (word.it.equalsIgnoreCase("die")) {
                    parsed.add(new ActionEcho(line.it,true));
                } else if (word.it.equalsIgnoreCase("mkdir")) {
                    parsed.add(new ActionMkdir(this, line.it));
                } else if (word.it.equalsIgnoreCase("delete") ||
                        word.it.equalsIgnoreCase("remove") ||
                        word.it.equalsIgnoreCase("erase")) {
                    parsed.add(new ActionDelete(this, line.it));
                } else if (word.it.equalsIgnoreCase("move")) {
                    parsed.add(new ActionMove(this, line.it, false));
                } else if (word.it.equalsIgnoreCase("copy")) {
                    parsed.add(new ActionMove(this, line.it, true));
                } else throw new UnknownInstructionException("Unknown instruction `"+word+"`");
            }
        }
    }

    public void run() throws Throwable {
        try {
            for (ScriptAction action : parsed) {
                action.run();
            }
            while (taskList.step(Executable.exec)) {
            }
        } finally {
            lock.Write();
        }
    }

    private void getWord(Ref<String> line, Ref<String> first) {
        int idx1 = line.it.indexOf(' ');
        if (idx1<0) {
            first.it = line.it;
            line.it = "";
        } else {
            first.it = line.it.substring(0,idx1);
            line.it = line.it.substring(idx1+1).trim();
        }
    }

    static Pattern argpattern = Pattern.compile("([%$])\\{(\\w+)}");
    private static String escapeValueReplacement(String string) {
        return string.replace("\\","\\\\").replace("$","\\$");
    }
    public static String injectRefs(String line) {
        if (line == null) return null;
        Matcher m = argpattern.matcher(line);
        StringBuffer r = new StringBuffer();
        while (m.find()) {
            if (m.group(2).equalsIgnoreCase("cwd")||m.group(2).equalsIgnoreCase("cd")) {
                m.appendReplacement(r, escapeValueReplacement(Executable.workdir.toString()));
            } else if (m.group(1).equals("$")) {
                String value = System.getenv(m.group(2));
                if (value==null) throw new RuntimeException("The specified environment variable was not set");
                if (value.isEmpty()) throw new RuntimeException("The specified environment variable is empty");
                m.appendReplacement(r, escapeValueReplacement(value));
            } else {
                ArgParser.Flag f = ArgParser.FindFlagByString("-"+m.group(2));
                if (f==null) throw new RuntimeException("Argument was not specified: "+m.group(2));
                if (!f.IsValueFlag()) throw new RuntimeException("The specified argument is not value flag");
                String value = ArgParser.GetFlagValue(f);
                if (value==null) throw new RuntimeException("The specified argument was empty");
                m.appendReplacement(r, escapeValueReplacement(value));
            }
        }
        m.appendTail(r);
        return r.toString();
    }

}
