package com.dosmike.spsauce.github;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.release.FileSet;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.tasks.ReleaseTask;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHReleaseBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HubRelease extends ReleaseTask {

    String owner,repository,commitish,tag;

    public HubRelease(FileSet files, String[] args) {
        super(files);
        assert args.length == 2;

        int sl = args[0].indexOf('/');
        int at = args[0].indexOf('@', sl + 1);
        if (sl < 0)
            throw new IllegalArgumentException("GitHub release fragment specifier is not owner/repository[@commitish]");

        this.owner = args[0].substring(0, sl).trim();
        if (at < 0) {
            this.repository = args[0].substring(sl + 1).trim();
            this.commitish = null;
        } else {
            this.repository = args[0].substring(sl+1,at).trim();
            this.commitish = args[0].substring(at+1).trim();
        }
        this.tag = args[1];
        if (this.owner.isEmpty() || this.repository.isEmpty() || this.tag.isEmpty())
            throw new IllegalArgumentException("Invalid format for GitHub release fragment specifier");
    }

    @Override
    public void run() {
        owner = BuildScript.injectRefs(owner);
        repository = BuildScript.injectRefs(repository);
        tag = BuildScript.injectRefs(tag);
        this.tag = this.tag.trim();
        if (this.tag.isEmpty()) throw new RuntimeException("Release tag resolved empty");
        System.out.println("└-> Creating GitHub Release: "+owner+"/"+repository+" "+(commitish==null?"<DEFAULT>":commitish)+" @"+tag);

        try {
            HubAuthorization auth = (HubAuthorization) BuildScript.getAuthorization("github");
            GHReleaseBuilder releaseBuilder = auth.hub.getRepository(owner + "/" + repository).createRelease(tag);
            if (commitish != null) releaseBuilder.commitish(commitish);
            releaseBuilder.name("Release "+tag);
            releaseBuilder.body("This build was automatically created by SPSauce");
            GHRelease release = releaseBuilder.create();
            for (FileSet.Entry e : files.getCandidates()) {
                if (e.isValid()) {
                    Path path = Executable.workdir.resolve(e.getProjectPath()).toAbsolutePath().normalize();
                    if (!path.startsWith(Executable.workdir)) {
                        System.err.println("Unable to attach file outside of working directory to GitHub Release: "+path);
                        continue;
                    }
                    String type = Files.probeContentType(path);
                    if (type == null) type = "text/plain";
                    release.uploadAsset(path.toFile(), type);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create GitHub Release", e);
        }
    }
}
