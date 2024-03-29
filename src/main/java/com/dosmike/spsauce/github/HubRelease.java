package com.dosmike.spsauce.github;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.release.FileSet;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.tasks.ReleaseTask;
import com.dosmike.spsauce.utils.BaseIO;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHReleaseBuilder;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
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
            if (auth == null)
                throw new RuntimeException("You need to auth GitHub before using GitHub releases");
            GHRepository repo = auth.hub.getRepository(owner + "/" + repository);
            //check if release tag exists
            GHRelease release = repo.getReleaseByTagName(tag);
            if (release == null) {
                //no release with that tag yet, make one
                GHReleaseBuilder releaseBuilder = repo.createRelease(tag);
                if (commitish != null) releaseBuilder.commitish(commitish);
                releaseBuilder.name("Release " + tag);
                releaseBuilder.body("This build was automatically created by SPSauce");
                release = releaseBuilder.create();
            }
            //attach files
            for (FileSet.Entry e : files.getCandidates()) {
                if (e.isValid()) {
                    try {
                        Path path = BaseIO.MakePathAbsoluteIfLegal(e.getProjectPath());
                        release.uploadAsset(path.toFile(), BaseIO.getMimeType(path));
                    } catch (IllegalArgumentException exception) {
                        System.err.println("Unable to attach file outside of working directory to GitHub Release: "+e.getProjectFile());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create GitHub Release", e);
        }
    }
}
