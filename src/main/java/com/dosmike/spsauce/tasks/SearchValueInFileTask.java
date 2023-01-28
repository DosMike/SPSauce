package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.utils.BaseIO;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchValueInFileTask implements Task {

    String variable;
    String target;
    Pattern search;
    String value;

    public SearchValueInFileTask(String variable, String file, Pattern pattern, String valueMapping) {
        this.variable = variable;
        this.target = file;
        this.search = pattern;
        this.value = valueMapping;
    }

    @Override
    public void run() throws Throwable {
        Matcher matcher = search.matcher(BaseIO.ReadFileContent(Executable.execdir, Paths.get(BuildScript.injectRefs(target))));
        StringBuilder sb = new StringBuilder();
        if (matcher.find()) {
            //dynamically generate "replacement" string to store in variable
            int from=0, next;
            while ((next=value.indexOf('\\',from))>=0 && next+1<value.length()) {
                sb.append(value, from, next);
                switch (value.charAt(next+1)) {
                    case '\\': {
                        sb.append('\\');
                        next++;
                        break;
                    }
                    case 'r': {
                        sb.append('\r');
                        next++;
                        break;
                    }
                    case 'n': {
                        sb.append('\n');
                        next++;
                        break;
                    }
                    case 't': {
                        sb.append('\t');
                        next++;
                        break;
                    }
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9': {
                        sb.append(matcher.groupCount() >= 1 ? matcher.group(value.charAt(next + 1) - '0') : "");
                        next++;
                        break;
                    }
                    default:
                        sb.append('\\');
                }
                from = next+1;
            }
            if (from < value.length()) {
                sb.append(value.substring(from));
            }

            //set said variable
            BuildScript.defineRef(variable, sb.toString());
        } else {
            throw new RuntimeException("Could not find value for pattern /"+search.toString()+"/ in "+target);
        }
    }
}
