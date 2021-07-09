package com.dosmike.spsauce.github;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.PluginSource;
import com.dosmike.spsauce.tasks.FetchTask;
import com.dosmike.spsauce.utils.ChunckReadable;
import com.dosmike.spsauce.utils.InOut;
import com.dosmike.spsauce.utils.Ref;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HubSource implements PluginSource {

    HubAuthorization auth;
    public HubSource(HubAuthorization auth) {
        if (auth == null)
            throw new RuntimeException("You need to auth GitHub before using GitHub dependencies");
        this.auth = auth;
    }

    @Override
    public Plugin search(String... criteria) throws IOException {
        if (criteria.length < 2 || criteria.length > 3) throw new IllegalArgumentException("search criteria requires: <group>/<repo> <tag> [archive]");

        GHRepository repo = auth.hub.getRepository(criteria[0]);
        Plugin plugin = new Plugin();
        plugin.name = repo.getName();
        plugin.game = "Unknown";
        plugin.homepage = "https://github.com/"+repo.getFullName();

        if (criteria[1].endsWith("-SNAPSHOT")) {
            //download specific branch
            String branchName = criteria[1].substring(0,criteria[1].length()-9);
            GHBranch branch = repo.getBranch(branchName);
            plugin.version = branch.getSHA1();
            plugin.downloadRef = repo;
        } else if (criteria[1].equalsIgnoreCase("latest")) {
            String defaultBranch = repo.getDefaultBranch();
            GHBranch branch = repo.getBranch(defaultBranch);
            plugin.version = branch.getSHA1();
            plugin.downloadRef = repo;
        } else {
            GHRelease release = repo.getReleaseByTagName(criteria[1]);
            plugin.version = release.getTagName();
            if (criteria.length == 3 && (criteria[2].endsWith(".zip") || criteria[2].endsWith(".tar.gz") || criteria[2].endsWith(".7z"))) {
                for (GHAsset asset : release.listAssets()) {
                    if (asset.getName().equalsIgnoreCase(criteria[2])) {
                        plugin.packageurl = asset.getBrowserDownloadUrl();
                        break;
                    }
                }
            }
            if (plugin.packageurl == null)
                plugin.packageurl = release.getZipballUrl();
        }
        return plugin;
    }

    @Override
    public boolean fetch(Plugin dep) throws IOException {
//        if (dep.packageurl == null) return false;
        Ref<String> filename = new Ref<>();
        Path archive = Executable.workdir.resolve(Paths.get("spcache", "download", "."));
        InOut.MakeDirectories(Executable.workdir, Paths.get("spcache", "download"));
        if (dep.packageurl != null)
            InOut.DownloadURL(dep.packageurl, archive, null, filename);
        else if (dep.downloadRef instanceof GHRepository) {
            filename.it = (dep.name+"_"+dep.version+".zip").replaceAll("[^\\w.]","_");
            final Path finalArchive = archive = archive.getParent().resolve(filename.it);
//            String branch = null;
//            if (dep.version.endsWith("-SNAPSHOT")) branch = dep.version.substring(0,dep.version.length()-9);
            //dep.version is a commit sha1
            String hash = ((GHRepository) dep.downloadRef).readZip(isf-> InOut.StreamToFile(ChunckReadable.chunks(isf), finalArchive, null),dep.version);
        } else return false; //no download given?
        if (!Files.exists(archive))
            throw new IOException("Download failed for "+dep.name);
        System.out.println("Downloaded "+filename.it +", extracting...");
        archive = archive.getParent().resolve(filename.it).normalize();
        Path libs = Executable.workdir.resolve("spcache");
        if (InOut.Unpack(archive, libs, FetchTask.SOURCEMOD_ARCHIVE_ROOT, InOut::FileExtractFilter)==0) {
            System.out.println("Archive has bad structure, guessing file paths!");
            if (InOut.UnpackUnordered(archive, libs, InOut::FileExtractFilter) == 0)
                throw new IOException("Failed to extract " + filename.it);
        }
        Files.deleteIfExists(archive);
        return true;
    }

    @Override
    public void push(Plugin meta, File... resources) {

    }
}
