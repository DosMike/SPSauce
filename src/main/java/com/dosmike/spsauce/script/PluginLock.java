package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.utils.BaseIO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class PluginLock {

    Path lockFile = Executable.cachedir.resolve(Paths.get("sps.lock"));
    List<Plugin> lockedPlugins = new LinkedList<>();
    boolean changed = false;

    public PluginLock() throws IOException {
        if (!Files.isRegularFile(lockFile)) {
            //cache directory is allowed to be ensured fully
            Files.createDirectories(Executable.cachedir);
            Path gitignore = lockFile.getParent().resolve(".gitignore");
            if (!Files.isRegularFile(gitignore)) {
                Files.write(gitignore, "# This file was generated automatically\n# The purpose of this project is to not push dependencies in your repo, so let's not do that\n\n**".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(lockFile)))) {
            String line;
            Plugin accu=null;
            while ((line=br.readLine())!=null) {
                if (line.trim().isEmpty()) continue;
                if (!line.startsWith("\t")) {
                    if (accu != null) lockedPlugins.add(accu);
                    accu = new Plugin();
                    accu.name = line.trim();
                } else {
                    if (accu == null) throw new IOException("Lock file was corrupted");
                    String[] kv = keyValue(line);
                    if (kv[0].equalsIgnoreCase("version")) accu.version = kv[1];
                    else if (kv[0].equalsIgnoreCase("game")) accu.game = kv[1];
                    else if (kv[0].equalsIgnoreCase("homepage")) accu.homepage = kv[1];
                    else if (kv[0].equalsIgnoreCase("source")) accu.packageurl = kv[1];
                    //everything else might be injected data by other tools
                }
            }
            if (accu!=null) lockedPlugins.add(accu);
        }
    }

    private String[] keyValue(String line) {
        line = line.trim();
        int idx1 = line.indexOf(':');
        if (idx1<0) {
            return new String[]{line,""};
        } else {
            return new String[]{line.substring(0,idx1),line.substring(idx1+1).trim()};
        }
    }

    public Plugin Find(String pluginName) {
        return lockedPlugins.stream().filter(p->p.name.equalsIgnoreCase(pluginName)).findAny().orElse(null);
    }

    public Plugin FindLock(Plugin search) {
        for (Plugin plugin : lockedPlugins) {
            if (plugin.homepage != null && plugin.homepage.equals(search.homepage)) return plugin;
            if (plugin.packageurl != null && plugin.packageurl.equals(search.packageurl)) return plugin;
            if (plugin.name.equals(search.name)) return plugin;
        }
        return null;
    }

    public void PatchName(Plugin search, Function<Plugin,Plugin> patcher) {
        Plugin contained = FindLock(search);
        if (contained==null) throw new IllegalArgumentException("Could not find lock for search lookup");
        Plugin patched = patcher.apply(contained);
        lockedPlugins.remove(search);
        lockedPlugins.add(patched);
        changed = true;
    }

    public void Lock(Plugin plugin) {
        if (FindLock(plugin)!=null) throw new IllegalStateException("Plugin "+plugin.name+" is already locked");
        lockedPlugins.add(plugin);
        changed = true;
    }

    public void ForceWrite() {
        changed = true;
    }

    public void Write() throws IOException {
        if (!changed) return;
        try (OutputStream out = Files.newOutputStream(lockFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            for (Plugin lock : lockedPlugins)
                lock.writeLock(out);
        }
    }
}
