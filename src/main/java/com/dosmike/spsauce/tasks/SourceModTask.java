package com.dosmike.spsauce.tasks;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.Plugin;
import com.dosmike.spsauce.Task;
import com.dosmike.spsauce.am.AMSource;
import com.dosmike.spsauce.script.PluginLock;
import com.dosmike.spsauce.utils.InOut;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SourceModTask implements Task {

    private String branch;
    private String build;
    private PluginLock lock;
    public SourceModTask(PluginLock lock, String branch, String build) {
        this.branch = branch;
        this.build = build;
        this.lock = lock;
    }

    @Override
    public void run() throws Throwable {
        if (ready()) return;
        AMSource.waitNextRequest();
        System.out.println("Fetching SourceMod "+branch+"@"+build+" for "+Executable.OS.name()+"...");
        String baseUrl = "https://sm.alliedmods.net/smdrop/"+branch+"/";
        boolean latest = build.equals("latest");
        String target = null, fname = null;
        if (latest) {
            HttpURLConnection con = InOut.PrepareConnection(baseUrl+"sourcemod-latest-"+Executable.OS.name().toLowerCase());
            InOut.CheckHTTPCode(con);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                fname = br.readLine();
                target = baseUrl + fname;
            }
        } else {
            HttpURLConnection con = InOut.PrepareConnection(baseUrl);
            InOut.CheckHTTPCode(con);
            Document doc = Jsoup.parse(con.getInputStream(), "UTF-8", baseUrl);
            for (Element e : doc.select("li a")) {
                if (e.text().contains("-git" + build + "-") && e.text().contains(Executable.OS.name().toLowerCase())) {
                    target = e.absUrl("href");
                    fname = e.text().trim();
                }
            }
        }
        if (target == null) throw new IOException("Could not locate SourceMod");
        Path archive = Paths.get("spcache", "download", fname);
        InOut.MakeDirectories(Executable.workdir, archive.getParent());
        archive = Executable.workdir.resolve(archive);
        if (!Files.exists(archive)) {
            AMSource.waitNextRequest();
            InOut.DownloadURL(target, archive, null, null);
        }
        if (InOut.Unpack(archive, Executable.workdir.resolve("spcache"), InOut.SOURCEMOD_ARCHIVE_ROOT, null)==0)
            throw new IOException("Unpacking SourceMod failed!");
        else {
            Files.deleteIfExists(archive);
            Plugin smlock = new Plugin();
            smlock.name = "sourcemod";
            smlock.version = branch+"-"+build;
            lock.Lock(smlock);
        }
    }

    private boolean ready() {
        Path baseDir = Executable.workdir.resolve(Paths.get("spcache","sourcemod","scripting"));
        if (Executable.OS == Executable.OperatingSystem.Windows)
            return Files.isRegularFile(baseDir.resolve("spcomp.exe"));
        else if (Executable.OS == Executable.OperatingSystem.Linux || Executable.OS == Executable.OperatingSystem.Mac)
            return Files.isRegularFile(baseDir.resolve("spcomp"));
        else
            throw new UnsupportedOperationException("SourceMod is not available for your OS");
    }

}
