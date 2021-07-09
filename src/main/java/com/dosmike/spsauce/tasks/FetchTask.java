package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.PluginSource;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.script.PluginLock;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

public class FetchTask implements Task {

    private final PluginSource at;
    private final Plugin dep;
    private final PluginLock lock;

    public static Predicate<Path> SOURCEMOD_ARCHIVE_ROOT = path-> path.endsWith(Paths.get("addons"));

    public FetchTask(PluginLock lock, PluginSource authenticatedSource, Plugin dependency) {
        this.lock = lock;
        this.at = authenticatedSource;
        this.dep = dependency;
    }

    public void run() throws IOException{
        if (!at.fetch(dep)) throw new IOException("Could not fetch dependency %s"+dep);
        lock.Lock(dep);
    }

}
