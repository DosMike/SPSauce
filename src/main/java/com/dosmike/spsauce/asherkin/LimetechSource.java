package com.dosmike.spsauce.asherkin;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.PluginSource;
import com.dosmike.spsauce.utils.ArchiveIO;
import com.dosmike.spsauce.utils.BaseIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LimetechSource implements PluginSource {

    @Override
    public Plugin search(String... criteria) throws IOException {
        if (criteria.length < 1 || criteria.length > 2) throw new IllegalArgumentException("search criteria requires String[]: <project> [version/build]");
        String project=criteria[0], version=criteria.length==2?criteria[1]:null;
        boolean latest = version != null;

        HttpURLConnection con = BaseIO.PrepareConnection("https://builds.limetech.io/?project="+ URLEncoder.encode(project, "UTF-8"));
        BaseIO.CheckHTTPCode(con);
        Document doc = Jsoup.parse(con.getInputStream(), "UTF-8", "https://builds.limetech.io/");
        Plugin plugin = new Plugin();
        plugin.name = project;
        plugin.game = "Unknown";
        //plugin.homepage =
        for (Element e : doc.select("td a")) {
            String href = e.attr("href");
            if (href.contains(Executable.OS.name().toLowerCase())) {
                if (latest || href.contains("-"+version+"-") || href.contains("-git"+version+"-") || href.contains("-hg"+version+"-")) {
                    plugin.packageurl = e.absUrl("href");
                    plugin.version = e.text().trim();
                    return plugin;
                }
            }
        }
        throw new IOException("Plugin not found");
    }

    @Override
    public boolean fetch(Plugin dep) throws IOException {
        if (dep.packageurl == null) return false;
        String filename = dep.packageurl.substring(dep.packageurl.lastIndexOf('/')+1);
        Path archive = Executable.cachedir.resolve(Paths.get("download", filename)).normalize();
        BaseIO.MakeDirectories(Executable.cachedir, Paths.get("download"));
        BaseIO.DownloadURL(dep.packageurl, archive, null, null);
        if (!Files.exists(archive))
            throw new IOException("Download failed for "+dep.name);
        System.out.println("Downloaded "+filename+", extracting...");
        Path libs = Executable.cachedir;
        if (ArchiveIO.Unpack(archive, libs, ArchiveIO.SOURCEMOD_ARCHIVE_ROOT, ArchiveIO::FileExtractFilter)==0)
            throw new IOException("Failed to extract "+filename);
        Files.deleteIfExists(archive);
        return true;
    }

    @Override
    public void push(Plugin meta, File... resources) {

    }
}
