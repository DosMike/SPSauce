package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.PluginSource;
import com.dosmike.spsauce.am.AMSource;
import com.dosmike.spsauce.asherkin.LimetechSource;
import com.dosmike.spsauce.github.HubAuthorization;
import com.dosmike.spsauce.github.HubSource;
import com.dosmike.spsauce.raw.DirectSource;
import com.dosmike.spsauce.tasks.FetchTask;

public class ActionDependency implements ScriptAction {

    BuildScript context;
    String source;
    String[] args;
    ActionDependency(BuildScript context, String platform, String args) {
        this.context = context;
        this.source = platform.toLowerCase();
        this.args = args.split(" ");
    }

    @Override
    public void run() throws Throwable {
        System.out.println("Discovering "+source+" dependency: "+String.join(" ",args));
        Plugin search;
        PluginSource ps;
        if (source.equals("am") || source.matches("^forums?$")) {
            ps = new AMSource();
        } else if (source.equals("github")) {
            HubAuthorization auth = (HubAuthorization) context.authorizationMap.get("github");
            ps = new HubSource(auth);
        } else if (source.equals("limetech")) {
            ps = new LimetechSource();
        } else if (source.equals("raw")) {
            ps = new DirectSource();
        } else throw new RuntimeException("Unknow plugin source: "+source);
        search = ps.search(args);
        if (search == null)
            throw new RuntimeException("Could not find source");

        Plugin existing = context.lock.FindLock(search);
        if (existing == null)
            context.taskList.and(new FetchTask(context.lock, ps,search));
        else {
            if (existing.version != null)
                System.out.println("  Already locked version "+existing.version);
            else
                System.out.println("  Already locked");
        }
    }
}
