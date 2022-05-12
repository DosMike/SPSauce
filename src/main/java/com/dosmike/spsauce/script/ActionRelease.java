package com.dosmike.spsauce.script;

import com.dosmike.spsauce.am.AMRelease;
import com.dosmike.spsauce.github.HubRelease;
import com.dosmike.spsauce.release.ZIPRelease;
import com.dosmike.spsauce.release.FileSet;
import com.dosmike.spsauce.release.UpdaterRelease;
import com.dosmike.spsauce.tasks.ReleaseTask;
import com.dosmike.spsauce.utils.Nullable;
import com.dosmike.spsauce.utils.Strings;

import java.util.LinkedList;
import java.util.List;

public class ActionRelease implements ScriptAction {

    private final List<String> what;
    protected BuildScript context;
    private final String[] args;
    private final Class<? extends ReleaseTask> task;

    public ActionRelease(@Nullable BuildScript context, List<String> files, String type, String args) {
        this.context = context;
        this.what = new LinkedList<>(files);
        this.args = Strings.translateCommandline(args);
        if (type.equals("github")) {
            task = HubRelease.class;
        } else if (type.equals("am") || type.matches("^forums?$")) {
            task = AMRelease.class;
        } else if (type.equals("zip")) {
            task = ZIPRelease.class;
        } else if (type.equals("updater")) {
            task = UpdaterRelease.class;
        } else {
            throw new IllegalArgumentException("Unknown release type \"" + type + "\"");
        }
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(()->{
            FileSet resolved = new FileSet();
            for (String path : what) {
                resolved.addFile(BuildScript.injectRefs(path));
            }
            if (resolved.getCandidates().stream().noneMatch(FileSet.Entry::isValid)) {
                throw new RuntimeException("Release FileSet did not contain any existing entries");
            }
            for (int i = 0; i < args.length; i++) {
                args[i] = BuildScript.injectRefs(args[i]);
            }
            task.getDeclaredConstructor(FileSet.class, String[].class).newInstance(resolved, args).run();
        });
    }

}
