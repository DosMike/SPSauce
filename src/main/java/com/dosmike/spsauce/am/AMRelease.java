package com.dosmike.spsauce.am;

import com.dosmike.spsauce.release.FileSet;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.tasks.ReleaseTask;
import com.dosmike.spsauce.utils.WebSoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AMRelease extends ReleaseTask {

    String version;
    long threadId;

    public AMRelease(FileSet files, String[] args) {
        super(files);
        assert args.length == 2;

        this.threadId = Long.parseLong(args[0]);
        this.version = args[1].trim();
    }

    @Override
    public void run() throws Exception {
        AMAuthorization auth = (AMAuthorization) BuildScript.getAuthorization("AlliedMods");
        WebSoup web = auth.getBrowser();
        //pull up editor and upload fileset
        String editUrl = getEditUrl(auth);
        Document editor = web.query(editUrl, null);
        web.wait(WebSoup.DEFAULT_WAIT);
        //pull up attachment editor
        //  keep in mind that we're in no-script land
        String attachUrl = Objects.requireNonNull(editor.selectFirst("a[target=manageattach]")).attr("href");
        Document attachEditor = web.query(attachUrl, null);
        attachUrl = Objects.requireNonNull(attachEditor.selectFirst("form")).attr("action");
        //delete all existing attachments
        Element element;
        while ((element = attachEditor.selectFirst("form input[name^=delete]"))!=null) {
            Element tr = element;
            while (tr.text().isEmpty()) tr = tr.parent();
            String filename = Objects.requireNonNull(tr.getElementsByTag("a").first()).text();
            System.out.println(" - Removing old attachment `"+filename+'`');

            WebSoup.MultiPartForm form = prepAttachmentEditForm(attachEditor);
            form.put(element);
            form.add("attachment[]", WebSoup.MultiPartFormValue.EMPTY_FILE);
            form.add("attachment[]", WebSoup.MultiPartFormValue.EMPTY_FILE);
            form.add("attachment[]", WebSoup.MultiPartFormValue.EMPTY_FILE);
            attachEditor = web.submitForm(attachUrl, form);
            web.wait(WebSoup.DEFAULT_WAIT);
        }
        //upload fileset 3-file wise
        Set<Path> pack = new HashSet<>();
        for (FileSet.Entry entry : files.getCandidates()) {
            int maxsize;
            switch (entry.getType()) {
                // i assume *1000 conversion to be conservative
                case Extension:
                    maxsize = 5720000; break;
                case ModConfig:
                case PluginConfig:
                    maxsize = 29300; break;
                case Plugin:
                case GameData:
                case PluginInclude:
                case PluginSource:
                case Translation:
                    maxsize = 1000000; break;
                default:
                    maxsize=0;
            }
            if (maxsize > 0 && Files.size(entry.getProjectPath()) <= maxsize) {
                pack.add(entry.getProjectPath());
                if (pack.size()>=3) {
                    System.out.println(" + Uploading batch: "+pack.stream().map(p->p.getFileName().toString()).collect(Collectors.joining(", ")));
                    WebSoup.MultiPartForm form = prepAttachmentEditForm(attachEditor);
                    for (Path p : pack) form.add("attachment[]", p); pack.clear();
                    form.put(element);
                    form.put(attachEditor.selectFirst("form input[name=upload]"));
                    attachEditor = web.submitForm(attachUrl, form);
                    web.wait(WebSoup.DEFAULT_WAIT);
                }
            }
        }
        if (pack.size() > 0) {
            System.out.println(" + Uploading batch: "+pack.stream().map(p->p.getFileName().toString()).collect(Collectors.joining(", ")));
            WebSoup.MultiPartForm form = prepAttachmentEditForm(attachEditor);
            for (Path p : pack) form.add("attachment[]", p); pack.clear();
            form.put(element);
            form.put(attachEditor.selectFirst("form input[name=upload]"));
            web.submitForm(attachUrl, form);
            web.wait(WebSoup.DEFAULT_WAIT);
        }
        //pull form from main editor window
        String actionUrl = Objects.requireNonNull(editor.selectFirst("form[name=vbform]")).attr("action");
        if (actionUrl.isEmpty()) throw new IllegalStateException("Can not update version from post editor");
        //don't add all submit buttons, only actual inputs and hidden stuff for now
        WebSoup.MultiPartForm form = new WebSoup.MultiPartForm(editor.selectFirst("form[name=vbform]"));
        //set/replace version information
        form.set("reason", "Automated Plugin Update to "+version);
        form.set("version", version);
        //"use" the save button
        form.put(editor.getElementById("vB_Editor_001_save"));
        web.queryForm(actionUrl, form);
    }

    private WebSoup.MultiPartForm prepAttachmentEditForm(Document document) {
        WebSoup.MultiPartForm form = new WebSoup.MultiPartForm();
        form.put(document.selectFirst("form input[name=s]"));
        form.put(document.selectFirst("form input[name=s]"));
        form.put(document.selectFirst("form input[name=securitytoken]"));
        form.put(document.selectFirst("form input[name=do]"));
        form.put(document.selectFirst("form input[name=t]"));
        form.put(document.selectFirst("form input[name=f]"));
        form.put(document.selectFirst("form input[name=p]"));
        form.put(document.selectFirst("form input[name=poststarttime]"));
        form.put(document.selectFirst("form input[name=editpost]"));
        form.put(document.selectFirst("form input[name=posthash]"));
        form.put(document.selectFirst("form input[name=MAX_FILE_SIZE]"));
        form.put("attachmenturl[]", ""); //we don't use this
        return form;
    }

    private String getEditUrl(AMAuthorization auth) throws IOException {
        WebSoup web = auth.getBrowser();
        //validate post/thread
        Document thread = web.query("showthread.php?t=" + threadId, null);
        web.wait(WebSoup.DEFAULT_WAIT);
        //we are on page one, post one will be the thread-post
        Element post = thread.selectFirst("#posts table[id^=post]");
        if (post == null || !post.text().contains("Plugin ID")) {
            throw new RuntimeException("Could not find first post in thread");
        }
        //check that this is a plugin and that we're author
        Element elem = post.selectFirst("table[class=panel] div");
        if (elem == null || !elem.text().contains("Plugin ID")) {
            throw new RuntimeException("Specified Thread is not a plugin thread!");
        }
        elem = post.selectFirst("a[class=bigusername]");
        if (elem == null || !auth.isSelf(elem.text())) {
            throw new RuntimeException("Thread not authored by authenticated user");
        }
        //query edit page
        elem = post.selectFirst("a[href^=editpost]");
        String editUrl;
        if (elem == null || (editUrl = elem.attr("href")).isEmpty())
            throw new RuntimeException("Could not start post edit");

        return editUrl;
    }

}
