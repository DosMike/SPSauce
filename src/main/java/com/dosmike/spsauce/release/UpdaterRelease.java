package com.dosmike.spsauce.release;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.tasks.ReleaseTask;
import com.dosmike.spsauce.utils.BaseIO;
import com.dosmike.valvekv.KVObject;
import com.dosmike.valvekv.KeyValueIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Support for the Updater plugin
 * <a href="https://forums.alliedmods.net/showthread.php?t=169095">AlliedMods Forums</a>
 * <a href="https://github.com/Teamkiller324/Updater">Latest Fork?</a>
 */
public class UpdaterRelease extends ReleaseTask {

    String updaterFile;
    String version;

    public UpdaterRelease(FileSet files, String[] args) {
        super(files);
        if (args.length != 2)
            throw new RuntimeException("Release action 'updater' expected 2 arguments, "+args.length+" passed: <updaterFile> <version>");
        this.updaterFile = args[0];
        this.version = args[1];
    }

    @Override
    public void run() {
        updaterFile = BuildScript.injectRefs(updaterFile);
        version = BuildScript.injectRefs(version);
        System.out.println("└-> Patching updater file: "+updaterFile);

        Path upf = Executable.execdir.resolve(updaterFile).toAbsolutePath().normalize();
        Path uph = upf.getParent().resolve('.'+upf.getFileName().toString()+".hash");
        if (!upf.startsWith(Executable.execdir))
            throw new IllegalArgumentException("Updater file is not in working directory");
        KVObject config = new KVObject();
        Map<String,String> fileHashes = new HashMap<>();
        try {
            if (Files.exists(upf)) config = KeyValueIO.loadFrom(upf);
            if (Files.exists(uph)) {
                KVObject hashes = KeyValueIO.loadFrom(uph).get("hashes").asObject();
                for (String s : hashes.getKeys()) {
                    fileHashes.put(s, hashes.getAsString(s));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read previous updater file");
        }

        try {
            processUpdate(files, config.getAsObject("Updater", new KVObject()), fileHashes);
        } catch (IOException e) {
            throw new RuntimeException("Could not process files for update", e);
        }

        try {
            //save updater file
            KeyValueIO.saveTo(upf, config);
            //save hashes
            KVObject root = new KVObject();
            KVObject hashes = root.getAsObject("hashes", new KVObject());
            for (Map.Entry<String, String> h : fileHashes.entrySet()) {
                hashes.set(h.getKey(), h.getValue());
            }
            KeyValueIO.saveTo(uph, root);
        } catch (IOException e) {
            throw new RuntimeException("Could not write updater file");
        }
    }

    private void processUpdate(FileSet files, KVObject config, Map<String,String> hashes) throws IOException {
        KeyValueIO.EnableEscapeSequenceParsing(false);
        boolean updatePatchBlock;
        KVObject block;
        // check header
        block = config.getAsObject("Information", new KVObject());
        //can we patch?
        String lastVersion = block.getAsObject("Version", new KVObject()).getAsString("Latest", "");
        updatePatchBlock = !lastVersion.isEmpty() && //there is no last version, this is a newly generated file
                !(lastVersion.equalsIgnoreCase(version)); //in case of a rerun we shouldn't nuke the patch block
        if (!lastVersion.isEmpty()) {
            //if this is a rerun, we drop all patch data, don't want to fiddle with what changed in that case
            // just download everything again and it should be fine
            if (updatePatchBlock)
                block.getAsObject("Version").set("Previous", lastVersion);
            else
                block.getAsObject("Version").deleteAll("Previous");
        }
        //set latest version and notes
        block.getAsObject("Version").set("Latest", version);
        block.deleteAll("Notes");
        block.push("Notes", "Version "+version+" was pushed by SPSauce");
        block.push("Notes", "More information on the project page");
        // write files block
        block = config.getAsObject("Files", new KVObject());
        //clear out old data first
        block.deleteAll("Patch");
        Map<String,String> newHashes = new HashMap<>();

        //put all files with different hash / null hash into the patch block
        for (FileSet.Entry e : files.getCandidates()) {
            if (!e.isValid() || !e.isSpFile()) continue;
            //get file type: plugin/source
            String group;
            switch (e.getType()) {
                case ProjectMeta:
                    continue; // don't need readmes on the server
                case PluginSource:
                case PluginInclude:
                    group = "Source";
                    break;
                default:
                    group = "Plugin";
                    break;
            }
            //compute new hash & compare
            String upath = e.getUpdaterFile();
            String hash = BaseIO.GetFileHash(e.getProjectPath(),"SHA-256");
            newHashes.put(upath, hash);
            if (!hash.equalsIgnoreCase(hashes.get(upath)) && updatePatchBlock) { //file is new (null in map) or different
                block.getAsObject("Patch", new KVObject()).push(group, upath);
            }
            //put regular file
            block.push(group, upath);
        }
        //update hashes
        hashes.clear();
        hashes.putAll(newHashes);
    }

}
