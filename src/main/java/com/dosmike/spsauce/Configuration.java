package com.dosmike.spsauce;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** this class is loading values from file system configurations in common directories to provide defaults or fallbacks
 * for values that are not overridden by flags or have no default otherwise */
public class Configuration {

    public Configuration() {
        Map<String,String> ini = load(this);
        cacheDirectory = getPathOrDefault(ini, "directories.plugincache", ".spcache");
    }

    public Path cacheDirectory;

//region Parsing

    private static Path getPathOrDefault(Map<String,String> map, String key, String defaultPath) {
        try {
            return Paths.get(map.getOrDefault("directories.plugincache", ".spcache"));
        } catch (InvalidPathException exception) {
            return Paths.get(defaultPath);
        }
    }

    private static Path getConfigPath() {

        if (Executable.OS == Executable.OperatingSystem.Windows) {
            Path config = Paths.get(System.getenv("HOMEDRIVE") + System.getenv("HOMEPATH"), ".spsauce", "spsauce.ini");
            if (Files.exists(config)) return config;

            config = Paths.get(System.getenv("LOCALAPPDATA"), "SPSauce", "spsauce.ini");
            if (Files.exists(config)) return config;
        } else {
            Path config = Paths.get(System.getenv("HOME"), ".spsauce", "spsauce.ini");
            if (Files.exists(config)) return config;

            config = Paths.get("/usr/etc/.spsauce/spsauce.ini");
            if (Files.exists(config)) return config;

            config = Paths.get("/etc/.spsauce/spsauce.ini");
            if (Files.exists(config)) return config;
        }
        return null;
    }

    private static Map<String,String> load(Configuration config) {
        HashMap<String,String> ini = new HashMap<>();
        Path configPath = getConfigPath();
        if (configPath == null) return ini;

        try (BufferedReader reader = Files.newBufferedReader(configPath)) {
            String group="",line;
            int lineno=0;

            while ((line = reader.readLine())!=null) {
                lineno+=1;

                line = line.replaceFirst("^\\s+", ""); //left-trim
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue; //empty or comment

                if (line.startsWith("[")) {
                    line = line.trim();
                    if (!line.endsWith("]")) throw new RuntimeException("Ini group name not closed on line "+lineno);

                    group = line.substring(1,line.length()-1)+".".toLowerCase(Locale.ROOT);
                } else {
                    //find first separator
                    int colon = line.indexOf(":");
                    int equals = line.indexOf("=");
                    int sep = colon > 0 ? colon : equals;
                    if (equals > 0 && equals < sep) sep = equals;
                    if (sep <= 0) throw new RuntimeException("Ini key empty or missing on line "+lineno);

                    String key = line.substring(0,sep).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(sep+1).trim();
                    if (key.isEmpty()) throw new RuntimeException("Ini key empty on line "+lineno);

                    ini.put(group+key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load configs from "+configPath+". Fix or remove file.");
        }

        return ini;
    }

//endregion

}
