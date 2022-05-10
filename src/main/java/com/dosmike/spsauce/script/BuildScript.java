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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildScript {

    TaskList taskList = new TaskList();
    static Map<String, Authorization> authorizationMap = new HashMap<>(); //persis for interactive mode
    PluginLock lock;

    List<ScriptAction> actionList = new LinkedList<>();
    private boolean ignoreNextActionFromOS =false;
    private void pushAction(ScriptAction action) {
        if (ignoreNextActionFromOS) ignoreNextActionFromOS =false;
        else actionList.add(action);
    }

    public BuildScript(Path fromFile) throws IOException {
        this(Files.newInputStream(fromFile));
    }

    public BuildScript(InputStream inputStream) throws IOException {
        lock = new PluginLock();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            Ref<String> line=new Ref<>(), word=new Ref<>();
            int parseMode=0; //1 read fileset, 2 read lua
            List<String> altLines = new LinkedList<>();
            while ((line.it =br.readLine())!=null) {
                if (parseMode >= 2) {
                    if (line.it.equalsIgnoreCase("end script")) {
                        if (!ArgParser.IsFlagSet(Executable.fNoScripts))
                            pushAction(new ActionSubscript(this, parseMode, altLines));
                        altLines.clear();
                        parseMode = 0;
                    } else {
                        altLines.add(line.it);
                    }
                    continue;
                }
                line.it = line.it.trim();
                if (line.it.startsWith("//") || line.it.startsWith("#") || line.it.isEmpty()) continue;

                if (parseMode == 1) {
                    if (line.it.toLowerCase(Locale.ROOT).startsWith(":release")) {
                        line.it = line.it.substring(8).trim();
                        getWord(line,word);
                        String type = word.it;
                        pushAction(new ActionRelease(this, altLines, type, line.it));

                        altLines.clear();
                        parseMode = 0;
                    } else {
                        altLines.add(line.it.trim());
                    }
                    continue;
                }

                getWord(line, word);
                if (word.it.startsWith("@")) {
                    Executable.OperatingSystem OSonly;
                    if (word.it.equalsIgnoreCase("@windows")) OSonly = Executable.OperatingSystem.Windows;
                    else if (word.it.equalsIgnoreCase("@linux")) OSonly = Executable.OperatingSystem.Linux;
                    else if (word.it.equalsIgnoreCase("@mac")) OSonly = Executable.OperatingSystem.Mac;
                    else throw new UnknownInstructionException("Invalid OS filter: "+word.it);
                    if (!Executable.OS.equals(OSonly)) ignoreNextActionFromOS=true;
                    getWord(line, word); //consume @os filter and parse rest of statement (mostly important for parseMode switches)
                }

                if (word.it.equalsIgnoreCase("auth")) {
                    getWord(line, word);
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        pushAction(new ActionAuth(this, word.it, line.it.split(" ")));
                } else if (word.it.equalsIgnoreCase("sourcemod")) {
                    getWord(line, word);
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        pushAction(new ActionSourceMod(this, word.it, line.it));
                } else if (word.it.equalsIgnoreCase("dependency")) {
                    getWord(line, word);
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        pushAction(new ActionDependency(this, word.it, line.it));
                } else if (word.it.equalsIgnoreCase("clone")) {
                    if (!ArgParser.IsFlagSet(Executable.fOffline))
                        pushAction(new ActionClone(this, line.it));
                } else if (word.it.equalsIgnoreCase("compilepool")) {
                    TaskList.setCompilePoolSize(Integer.parseInt(line.it));
                } else if (word.it.equalsIgnoreCase("compile")||word.it.equalsIgnoreCase("spcomp")) {
                    pushAction(new ActionSpcomp(this, line.it));
                } else if (word.it.equalsIgnoreCase("exec")) {
                    if (!ArgParser.IsFlagSet(Executable.fNoExec))
                        pushAction(new ActionExec(this, line.it));
                } else if (word.it.equalsIgnoreCase("echo")) {
                    pushAction(new ActionEcho(null, line.it,false));
                } else if (word.it.equalsIgnoreCase("die")) {
                    pushAction(new ActionEcho(null, line.it,true));
                } else if (word.it.equalsIgnoreCase("echo!")) {
                    pushAction(new ActionEcho(this, line.it,false));
                } else if (word.it.equalsIgnoreCase("die!")) {
                    pushAction(new ActionEcho(this, line.it,true));
                } else if (word.it.equalsIgnoreCase("mkdir")) {
                    pushAction(new ActionMkdir(this, line.it));
                } else if (word.it.equalsIgnoreCase("delete") ||
                        word.it.equalsIgnoreCase("remove") ||
                        word.it.equalsIgnoreCase("erase")) {
                    pushAction(new ActionDelete(this, line.it));
                } else if (word.it.equalsIgnoreCase("move")) {
                    pushAction(new ActionMove(this, line.it, false));
                } else if (word.it.equalsIgnoreCase("copy")) {
                    pushAction(new ActionMove(this, line.it, true));
                } else if (word.it.equalsIgnoreCase("set")) {
                    pushAction(new ActionSetVariable(this, line.it));
                } else if (word.it.equalsIgnoreCase("with") && line.it.equalsIgnoreCase("files")) {
                    parseMode = 1;
                } else if (word.it.equalsIgnoreCase("script")) {
                    if (line.it.equalsIgnoreCase("lua")) parseMode = 2;
                    else throw new IllegalArgumentException("Unknown script language");
                } else if (word.it.equalsIgnoreCase("pucpatch")) {
                    pushAction(new ActionPUCPatch(this, line.it));
                } else throw new UnknownInstructionException("Unknown instruction `"+word+"`");
            }
            if (parseMode != 0) throw new RuntimeException("Unterminated switched syntax block - didn't return from mode "+parseMode);
        }
    }

    public void run() throws Throwable {
        try {
            for (ScriptAction action : actionList) action.run();
            while (taskList.canStep()) taskList.step(Executable.exec);
        } finally {
            authorizationMap.forEach((key, value) -> {
                if (value instanceof AutoCloseable) {
                    try { ((AutoCloseable) value).close(); }
                    catch (Exception e) { System.err.println("Failed to dispose authentication for " + key); }
                }
            });
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

    //a little wrapper to allow setting env values
    private static final Map<String, String> environment = new HashMap<>();
    public static String getEnvValue(String key) {
        return environment.containsKey(key) ? environment.get(key) : System.getenv(key);
    }
    public static void setEnvValue(String key, String value) {
        if (value==null||value.isEmpty()) environment.remove(key);
        else environment.put(key, value);
    }
    public static void applyEnvironment(ProcessBuilder subProcess) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            try {
                subProcess.environment().put(entry.getKey(), entry.getValue());
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                System.out.println("Could not set env variable '" + entry.getKey() + "'");
            }
        }
    }
    //and a wrapper to overshadow arg flags
    private static final Map<String, String> variable = new HashMap<>();
    public static void setVariable(String key, String value) {
        if (value==null||value.isEmpty()) variable.remove(key);
        else variable.put(key, value);
    }
    public static String parseVariable(String key) {
        if (argpattern.matcher(key).matches()) return injectRefs(key);
        else throw new IllegalArgumentException("Not a valid variable key");
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
                String value = getEnvValue(m.group(2));
                if (value==null) throw new RuntimeException("The specified environment variable was not set");
                if (value.isEmpty()) throw new RuntimeException("The specified environment variable is empty");
                m.appendReplacement(r, escapeValueReplacement(value));
            } else if (variable.containsKey(m.group(2))) {
                //as %var, meant to act like args, can overshadow args
                m.appendReplacement(r, escapeValueReplacement(variable.get(m.group(2))));
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
    public static void defineRef(String name, String value) {
        Matcher m = argpattern.matcher(name);
        if (!m.matches()) throw new IllegalArgumentException("Malformed variable name '"+name+"'");
        if (m.group(1).equals("$")) {
            setEnvValue(m.group(2), value);
        } else {
            setVariable(m.group(2), value);
        }
    }

    public static Authorization getAuthorization(String auth) {
        return authorizationMap.get(auth.toLowerCase(Locale.ROOT));
    }

}
