package com.dosmike.spsauce.utils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strings {
    public static String[] translateCommandline(String line) {
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

    public static String getTabSpacingToWidth(int inputWidth, int targetWidth, int tabWidth) {
        //we have to round up targetWidth to be a multiple of tabWidth
        targetWidth = (int) (Math.ceil( (double) targetWidth / tabWidth ) * tabWidth);
        //get the amount of tabs that fit in the leftover space rounded up
        int tabs = (int) Math.ceil( (double) (targetWidth-inputWidth) / tabWidth );
        //make a string with that amount of tabs
        StringBuilder sb = new StringBuilder(tabs);
        while (tabs>0) { sb.append('\t'); tabs--; }
        return sb.toString();
    }
    public static String getCharSpacingToWidth(int inputWidth, int targetWidth, char filler) {
        int chars = targetWidth-inputWidth;
        StringBuilder sb = new StringBuilder(chars);
        while (chars>0) { sb.append(filler); chars--; }
        return sb.toString();
    }
    public static String getSpacingFromModelineOptions(int inputWidth, int targetWidth, ModelineOptions options) {
        if (options.tabstop == 1)
            return getCharSpacingToWidth(inputWidth, targetWidth, '\t');
        else if (options.expandtab) {
            //round up to the next tab stop
            targetWidth = (int) (Math.ceil( (double) targetWidth / options.tabstop ) * options.tabstop);
            return getCharSpacingToWidth(inputWidth, targetWidth, ' ');
        }
        else return getTabSpacingToWidth(inputWidth, targetWidth, options.tabstop);
    }

    public static class ModelineOptions {
        int tabstop=4;
        boolean expandtab=false;
    }
    /** @return the parsed modeline options or null if not found */
    public static ModelineOptions getVimModelineTab(String comment) {
        comment = comment.trim();
        //search for a starting 'vim' indicator
        if (!comment.startsWith("vim")) return null;
        comment = comment.substring(3).trim();
        //require a following colon
        int col = comment.indexOf(':');
        if (col < 0 || (col>0 && !comment.substring(0,col).trim().isEmpty())) return null;
        comment = comment.substring(col+1).trim();
        //mode line either has values colon separated or space separated if prefixed with set
        String[] elements;
        if (comment.startsWith("set")) {
            Pattern toEnd = Pattern.compile("^(.*)(?<!\\\\):");
            Matcher match = toEnd.matcher(comment);
            if (!match.find()) return null; //idk man, it doesn't end
            elements = match.group(1).split(" +");
        } else {
            elements = comment.split(":");
        }
        //get the mode line options we care about
        ModelineOptions options = new ModelineOptions();
        for (String s : elements) {
            if (s.startsWith("ts=") || s.startsWith("tabstop="))
                options.tabstop = Math.max(1,Integer.parseInt(s.substring(s.indexOf('=')+1)));
            else if (s.equals("expandtab") || s.equals("et"))
                options.expandtab = true;
        }
        return options;
    }

}
