package com.dosmike.spsauce.utils;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/** Helps convert between MarkDown and AM BBCode. Tables are currently ignored! */
public class NotesConverter {

    String toBBCode(String markdown) {
        markdown = markdown
                //fix line-ends/breaks
                .replaceAll("\\r?\\n|\\r(?>!\\n)", "\n") //use linux line ends
                .replaceAll("(?<! {3,})\\n"," ") //line breaks in md are not literal!
                //let's make headers
                .replaceAll("(^|\\n)##### ([^\\n]*)(\\n|$)", "[H5]$2[/H5]\n")
                .replaceAll("(^|\\n)#### ([^\\n]*)(\\n|$)", "[H4]$2[/H4]\n")
                .replaceAll("(^|\\n)### ([^\\n]*)(\\n|$)", "[H3]$2[/H3]\n")
                .replaceAll("(^|\\n)## ([^\\n]*)(\\n|$)", "[H2]$2[/H2]\n")
                .replaceAll("(^|\\n)# ([^\\n]*)(\\n|$)", "[H1]$2[/H1]\n")
                .replaceAll("(^|\\n)([^\\n]+)\\n *-{3,} *(\\n|$)", "[H2]$2[/H2]\n")
                .replaceAll("(^|\\n)([^\\n]+)\\n *={3,} *(\\n|$)", "[H1]$2[/H1]\n")
                //inline code
                .replaceAll("`\\b(.*)\\b`", "[TT]$1[/TT]")
                //code block - ignoring [PHP]
                .replaceAll("(^|\\n)```(\\w+) *\\n(.*)\\n```(\\n|$)", "[CODE]$3[/CODE]")
                // b i s
                .replaceAll("\\*\\*\\b(.*)\\b\\*\\*", "[B]$1[/B]")
                .replaceAll("__\\b(.*)\\b__", "[B]$1[/B]")
                .replaceAll("\\*\\b(.*)\\b\\*", "[I]$1[/I]")
                .replaceAll("_\\b(.*)\\b_", "[I]$1[/I]")
                .replaceAll("~~\\b(.*)\\b~~", "[S]$1[/S]")
        ;
        //do line based stuff - mainly lists
        String[] lines = markdown.split("\\n");
        List<String> converted = new LinkedList<>();
        Stack<Integer> listStack = new Stack<>();
        for (String line : lines) {
            int indent = 0;
            for (int i=0;i<line.length();i++) if (line.charAt(i)==' '||line.charAt(i)=='\t') indent++;
            boolean isList = indent < lines.length-2 && line.charAt(indent)=='*' && line.charAt(indent+1)==' ';
            if (!isList) {
                while (!listStack.empty()) {
                    listStack.pop();
                    converted.add("[/LIST]");
                }
            } else {
                while (!listStack.empty() && indent < listStack.lastElement()) {
                    listStack.pop();
                    converted.add("[/LIST]");
                }
                if (listStack.empty() || indent > listStack.lastElement()) {
                    listStack.push(indent);
                    converted.add("[LIST]");
                }
                converted.add("[*]"+line.substring(indent+1));
            }
        }
        //merge and return
        return String.join("\n", converted);
    }

}
