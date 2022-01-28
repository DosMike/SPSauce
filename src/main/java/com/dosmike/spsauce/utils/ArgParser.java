package com.dosmike.spsauce.utils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArgParser {

    public static class Flag {
        private final List<String> aliases = new LinkedList<>();
        private final boolean isValueFlag;
        private final String description;
        private Flag(String description, boolean hasValue) {
            this.description = description;
            this.isValueFlag = hasValue;
        }
        public boolean IsValueFlag() {return isValueFlag;}

        @Override
        public String toString() {
            return aliases.stream().map(x->"-"+x).collect(Collectors.joining("|"));
        }
        public String description() {
            return this.description;
        }
    }
    static List<Flag> _flags = new LinkedList<>();
    static List<Flag> setFlags = new LinkedList<>();
    static Map<Flag,String> vflags = new HashMap<>();
    static List<String> stringargs = new LinkedList<>();
    static boolean allowFlags = true;

    private static final Flag fHelp = RegisterFlag("Displays the help message", "?","-help");

    public static Flag RegisterFlag(String description, String... aliases) {
        Flag flag = new Flag(description, false);
        for (String s : aliases) {
            if (s.contains(" ")) throw new IllegalArgumentException("Flag names cannot contain spaces");
            if (s.startsWith("-")) flag.aliases.add(s.toLowerCase());
            else if (s.length() > 1) throw new IllegalArgumentException("Short flags are single letters");
            else flag.aliases.add(s);
        }
        _flags.add(flag);
        return flag;
    }
    public static Flag RegisterValueFlag(String description, String... aliases) {
        Flag flag = new Flag(description, true);
        for (String s : aliases) {
            if (s.contains(" ")) throw new IllegalArgumentException("Flag names cannot contain spaces");
            if (s.startsWith("-")) flag.aliases.add(s.toLowerCase());
            else if (s.length() > 1) throw new IllegalArgumentException("Short flags are single letters");
            else flag.aliases.add(s);
        }
        _flags.add(flag);
        return flag;
    }
    public static void Parse(String[] args) {
        for (int i=0; i<args.length; i++) {
            String arg = args[i];
            String peek = i+1 < args.length ? args[i+1] : null;
            if (allowFlags && arg.startsWith("-")) {
                if (arg.equals("--")) {
                    allowFlags = false;
                    continue;
                }
                Flag f = FindFlagByString(arg.substring(1));
                if (f == null) { //new flag - guess value flag
                    if (peek != null && arg.startsWith("--") && !peek.startsWith("-")) {
                        f = RegisterValueFlag("",arg.substring(1));
                    } else if (arg.length() == 2 && arg.startsWith("-")) {
                        f = RegisterFlag("",arg.substring(1));
                    } else {
                        stringargs.add(arg);
                        continue;
                    }
                }
                if (f.isValueFlag) {
                    if (peek == null) throw new IllegalArgumentException("Value flag has no value");
                    vflags.put(f, peek);
                    i++;//skip next
                }
                else setFlags.add(f);
            } else {
                stringargs.add(arg);
            }
        }
        if (IsFlagSet(fHelp)) PrintHelp();
    }
    public static boolean IsFlagSet(Flag flag) {
        return setFlags.contains(flag) || vflags.containsKey(flag);
    }
    public static String GetFlagValue(Flag flag) {
        if (!flag.isValueFlag) throw new IllegalArgumentException("Flag is no value flag");
        return vflags.get(flag);
    }
    public static Flag FindFlagByString(String alias) {
        return _flags.stream().filter(f->f.aliases.stream()
                //case-sensitive for single letter (short flags)
                .anyMatch(a->alias.length()>1 ? a.equalsIgnoreCase(alias) : a.equals(alias)))
                .findFirst().orElse(null);
    }
    /** un-tagged Strings are usually files */
    public static List<String> GetStringArgs() {
        return stringargs;
    }

    /** application arguments */
    public static String usageString="";
    public static String description="";
    public static void PrintHelp() {
        //usage
        String[] parts = ArgParser.class.getProtectionDomain().getCodeSource().getLocation().getFile().split("[/\\\\]");
        System.out.println("Usage: java -jar "+parts[parts.length-1]+" "+usageString);
        //desc
        System.out.println(description);
        System.out.println();
        //flags
        for (Flag f : _flags) {
            if (f.description.isEmpty()) continue; //is auto flag
            System.out.println("  "+f);
            int from = 0;
            while (from < f.description.length()) {
                int to = Math.min(f.description.length(), from+70);
                int to2 = f.description.lastIndexOf(' ',to);
                if (to2 > 0 && to < f.description.length()) {
                    System.out.println("      " + f.description.substring(from, to2));
                    from += to2+1;
                } else {
                    System.out.println("      " + f.description.substring(from, to));
                    from = to;
                }
            }
        }
        //halt
        System.exit(0);
    }
}
