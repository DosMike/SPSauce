package com.dosmike.spsauce.am;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.PluginSource;
import com.dosmike.spsauce.tasks.FetchTask;
import com.dosmike.spsauce.utils.InOut;
import com.dosmike.spsauce.utils.Ref;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

public class AMSource implements PluginSource {

    //  urls: https://forums.alliedmods.net/showthread.php?p=2744460
    private static String lastSourceUrl = null;
    private static Document lastDocument = null;
    private static Long lastQuery = 0L;

    public static synchronized void waitNextRequest() {
        long sinceLast = System.currentTimeMillis()-lastQuery;
        long queryCooldown = 2500;
        if (sinceLast < queryCooldown) try { Thread.sleep(queryCooldown-sinceLast); } catch (InterruptedException e) { return; }
        lastQuery = System.currentTimeMillis();
    }

    @Override
    public Plugin search(String... criteria) throws IOException {
        if (criteria.length < 1 || criteria.length > 2) throw new IllegalArgumentException("search criteria requires: [patch] <id|url>");
        String sc = criteria[0];
        if (sc.equals("patch")) {
            sc = criteria[1];
            if (sc.matches("^[0-9]+$")) {
                return searchByPostUrl("https://forums.alliedmods.net/showpost.php?p="+Integer.parseInt(sc));
            } else if (sc.matches("^https://forums\\.alliedmods\\.net/showpost\\.php\\?p=[0-9]+(?:&.*)?$")) {
                return searchByPostUrl(sc);
            }
        } else {
            if (sc.matches("^[0-9]+$")) {
                return searchByPostUrl("https://forums.alliedmods.net/showthread.php?t="+Integer.parseInt(sc));
            } else if (sc.matches("^https://forums\\.alliedmods\\.net/showthread\\.php\\?t=[0-9]+(?:&.*)?$")) {
                return searchByPostUrl(sc);
            }
        }
        throw new IllegalArgumentException("search criteria requires: full url or thread id");
    }
    private void loadDocument(String url) throws IOException {
        if (lastDocument != null && (lastDocument.location().equalsIgnoreCase(url) || lastSourceUrl.equalsIgnoreCase(url))) return; //we already parsed that
        //limit queries
        waitNextRequest();
        lastSourceUrl = url;
        lastDocument = null;
        HttpURLConnection con = InOut.PrepareConnection(url);
        InOut.CheckHTTPCode(con);
        lastDocument = Jsoup.parse(con.getInputStream(), "UTF-8", "https://forums.alliedmods.net/");
    }
    private Plugin searchByPostUrl(String url) throws IOException {
        loadDocument(url);
        Element element = lastDocument.selectFirst("#posts table.ttop table table.panel");
        Plugin data = new Plugin();
        data.homepage = url;
        if (element == null) {
            if (url.contains("showthread.php")) throw new IOException("Could not locate plugin metadata");
            //get post id back
            String tmp = url.substring(url.lastIndexOf('/')+1);
            tmp = tmp.substring(tmp.indexOf("?p=")+3);
            if (tmp.indexOf('&')>0) tmp = tmp.substring(0,tmp.indexOf('&'));
            data.version = tmp;
            data.name = "Patch from Post "+data.version;
            data.game = "Unknown";
            data.downloadRef = "PATCH";
            //find attachments
            element = lastDocument.selectFirst("#td_post_"+data.version+" fieldset table");
        } else {
            Elements metadata = element.select("td");
            String key = null;
            boolean readValue = false;
            for (Element cell : metadata) {
                String cellvalue = cell.text().trim();
                if (readValue) {
                    if ("version".equals(key)) {
                        data.version = cellvalue;
                    } else if ("game".equals(key)) {
                        data.game = cellvalue;
                    }
                    readValue = false;
                } else if (cellvalue.startsWith("Plugin Version")) {
                    key = "version";
                    readValue = true;
                } else if (cellvalue.startsWith("Plugin Game")) {
                    key = "game";
                    readValue = true;
                }
            }
            data.name = lastDocument.selectFirst("#poststop").parent().child(1).text();
            //find attachments
            element = lastDocument.selectFirst("#posts table.ttop fieldset table");
        }
        if (element == null) throw new IOException("The specified target did not contain a plugin/patch");
        data.amattachments = new SourceCluster();
        Elements links = element.select("a");
        for (Element link : links) {
            String text = link.text().trim();
            if (text.equals("Get Source")) data.amattachments.add(link.absUrl("href"), ".sp");
            else if (text.equals("Get Plugin")) data.amattachments.add(link.absUrl("href"), ".smx");
            else if (text.endsWith(".zip") || text.endsWith(".tar.gz") || text.endsWith(".7z")) {
                data.packageurl = link.absUrl("href");
                data.amattachments = null; //prefer archives
                break;
            }
            else if (text.indexOf('.') > 0) data.amattachments.add(link.absUrl("href"), text);
        }

        return data;
    }

    private static Predicate<Path> patchFilter = p->InOut.FileExtractFilter(p)&&Files.exists(p);

    @Override
    public boolean fetch(Plugin dep) throws IOException {
        if (dep.packageurl != null) {
            Ref<String> filename = new Ref<>();
            Path archive = Executable.workdir.resolve(Paths.get("spcache", "download", "."));
            InOut.MakeDirectories(Executable.workdir, Paths.get("spcache", "download"));
            InOut.DownloadURL(dep.packageurl, archive, null, filename);
            if (!Files.exists(archive))
                throw new IOException("Download failed for "+dep.name);
            System.out.println("Downloaded "+filename.it +", extracting...");
            archive = archive.getParent().resolve(filename.it).normalize();
            Path libs = Executable.workdir.resolve("spcache");
            if ("PATCH".equals(dep.downloadRef)) {
                if (InOut.Unpack(archive, libs, FetchTask.SOURCEMOD_ARCHIVE_ROOT, patchFilter) == 0)
                    throw new IOException("Failed to extract " + filename.it);
            } else {
                if (InOut.Unpack(archive, libs, FetchTask.SOURCEMOD_ARCHIVE_ROOT, InOut::FileExtractFilter) == 0)
                    throw new IOException("Cannot patch non-existing file, please ensure patches are applied after dependencies");
            }
            Files.deleteIfExists(archive);
            return true;
        } else {
            Path libs = Executable.workdir.resolve("spcache");
            Ref<String> filename = new Ref<>();
            int downloaded = 0;
            for (int i=0;i<dep.amattachments.size();i++) {
                Path torel = dep.amattachments.estimateTarget(i);
                if (torel != null) {
                    Path toabs = libs.resolve(torel);
                    if ("PATCH".equals(dep.downloadRef) && !patchFilter.test(toabs))
                        throw new IOException("Cannot patch non-existing file, please ensure patches are applied after dependencies");
                    else if (!InOut.FileExtractFilter(toabs)) continue;
                    InOut.MakeDirectories(libs, torel.getParent());
                    try {
                        InOut.DownloadURL(dep.amattachments.getUrl(i), toabs, null, filename);
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
