package com.dosmike.spsauce.raw;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.PluginSource;
import com.dosmike.spsauce.am.SourceCluster;
import com.dosmike.spsauce.utils.ArchiveIO;
import com.dosmike.spsauce.utils.BaseIO;
import com.dosmike.spsauce.utils.Ref;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DirectSource implements PluginSource {

    @Override
    public Plugin search(String... criteria) throws IOException {
        if (criteria.length != 1 || !criteria[0].startsWith("http")) throw new IllegalArgumentException("Raw source requires url");
        HttpURLConnection con = BaseIO.PrepareConnection(criteria[0]);
        con.setRequestMethod("HEAD");
        BaseIO.CheckHTTPCode(con);
        Plugin data = new Plugin();
        data.name = String.format("RawFile %08X", criteria[0].hashCode());
        data.homepage = criteria[0];
        data.game = "Unknown";
        //figure out if we deal with an archive:
        String info = con.getHeaderFields().entrySet().stream()
                .filter(e->"Content-Disposition".equalsIgnoreCase(e.getKey()))
                .map(e->String.join(";",e.getValue())).findFirst().orElse(null);
        String filename;
        if (info != null) {
            filename = BaseIO.ContentDispositionFilename(info);
        } else {
            String[] parts = con.getURL().getPath().split("/");
            filename = parts[parts.length-1];
        }
        if (filename.endsWith(".7z") || filename.endsWith(".tar.gz") || filename.endsWith(".zip"))
            data.packageurl = criteria[0];
        else {
            data.amattachments = new SourceCluster();
            if (SourceCluster.EstimateDirectoryByName(filename) == null)
                throw new IllegalArgumentException("Specified raw file has unsupported filetype ("+filename+")");
            data.amattachments.add(criteria[0], filename);
        }
        return data;
    }

    @Override
    public boolean fetch(Plugin dep) throws IOException {
        //copy of AMSource#fetch without "patch" related lines
        if (dep.packageurl != null) {
            Ref<String> filename = new Ref<>();
            Path archive = Executable.cachedir.resolve(Paths.get("download", "."));
            BaseIO.MakeDirectories(Executable.cachedir, Paths.get("download"));
            BaseIO.DownloadURL(dep.packageurl, archive, null, filename);
            if (!Files.exists(archive))
                throw new IOException("Download failed for "+dep.name);
            System.out.println("Downloaded "+filename.it +", extracting...");
            archive = archive.getParent().resolve(filename.it).normalize();
            Path libs = Executable.cachedir.resolve("addons");
           if (ArchiveIO.Unpack(archive, libs, ArchiveIO.SOURCEMOD_ARCHIVE_ROOT, ArchiveIO::FileExtractFilter) == 0)
                throw new IOException("Cannot patch non-existing file, please ensure patches are applied after dependencies");
            Files.deleteIfExists(archive);
            return true;
        } else {
            Path libs = Executable.cachedir;
            Ref<String> filename = new Ref<>();
            int downloaded = 0;
            for (int i=0;i<dep.amattachments.size();i++) {
                Path torel = dep.amattachments.estimateTarget(i);
                if (torel != null) {
                    Path toabs = libs.resolve(torel);
                    if (!ArchiveIO.FileExtractFilter(toabs)) continue;
                    BaseIO.MakeDirectories(libs, torel.getParent());
                    try {
                        BaseIO.DownloadURL(dep.amattachments.getUrl(i), toabs, null, filename);
                        System.out.println("Downloaded "+filename.it);
                        downloaded++;
                    } catch (IOException ex) {
                        System.err.println("Download attachment failed for "+dep.name+": "+ex.getMessage());
                    }
                }
            }
            return downloaded>0;
        }
    }

    @Override
    public void push(Plugin meta, File... resources) {

    }
}
