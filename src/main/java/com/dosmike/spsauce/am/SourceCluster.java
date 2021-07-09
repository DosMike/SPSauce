package com.dosmike.spsauce.am;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class SourceCluster {

    private List<String> files = new LinkedList<>();
    private List<String> names = new LinkedList<>();

    public void add(String url, String filename) {
        files.add(url);
        names.add(filename);
    }

    public int size() {
        return files.size();
    }
    public String getUrl(int n) {
        return files.get(n);
    }
    public Path estimateTarget(int n) {
        return EstimateDirectoryByName(names.get(n));
    }

    public static Path EstimateDirectoryByName(String filename) {
        String name = Paths.get(filename).getFileName().toString();
        if (name.endsWith(".sp")) {
            return Paths.get("addons","sourcemod","scripting",name);
        } else if (name.endsWith(".smx")) {
            return Paths.get("addons","sourcemod","plugins",name);
        } else if (name.endsWith(".inc")) {
            return Paths.get("addons","sourcemod","scripting","include",name);
        } else if (name.endsWith(".phrases.txt")) {
            return Paths.get("addons","sourcemod","translations",name);
        } else if (name.endsWith(".cfg")) {
            return Paths.get("addons","sourcemod","configs",name);
        } else if (name.endsWith(".games.txt")) {
            return Paths.get("addons","sourcemod","gamedata",name);
        } else {
            return null;
        }
    }

}
