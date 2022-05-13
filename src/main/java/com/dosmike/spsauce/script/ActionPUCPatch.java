package com.dosmike.spsauce.script;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.utils.Strings;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

/** Go through and update a file that is formatted according to the specifications at
 * <a href="https://forums.alliedmods.net/showthread.php?t=333430">the AlliedMods Forums</a> */
public class ActionPUCPatch implements ScriptAction {

    BuildScript context;
    String file,convar,version;

    public ActionPUCPatch(BuildScript context, String line) {
        this.context = context;
        int sep = line.indexOf(':');
        if (sep < 0) throw new IllegalArgumentException("Ill-formatted PUCPatch Statement");
        this.file = line.substring(0,sep).trim();
        String[] args = Strings.translateCommandline(line.substring(sep+1));
        this.convar = args[0];
        this.version = args[1];
    }

    @Override
    public void run() throws Throwable {
        context.taskList.and(()->{
            Path pucfile = Paths.get(BuildScript.injectRefs(file)).toAbsolutePath().normalize();
            String myConvar = BuildScript.injectRefs(convar);
            String myVersion = BuildScript.injectRefs(version);
            if (!pucfile.startsWith(Executable.workdir)) throw new IllegalArgumentException("PUC Index file is not within workdir");

            //validate that this is a puc index
            List<PUCEntry> entries = new LinkedList<>();
            int[] colWidth = new int[]{0,0,0,0};
            Strings.ModelineOptions options = null;
            boolean containsVersionConvar = false;
            for (String line : Files.readAllLines(pucfile)) {
                PUCEntry entry = PUCEntry.fromString(line);
                if (!entry.isComment) {
                    if (options == null)
                        options = new Strings.ModelineOptions();
                    //patch value
                    if (entry.convar.equalsIgnoreCase(myConvar)) {
                        entry.convar = myConvar;
                        entry.version = myVersion;
                        containsVersionConvar = true;
                    }
                    //collect column widths for formatting
                    if (entry.convar.length() > colWidth[0]) colWidth[0] = entry.convar.length();
                    if (entry.version.length() > colWidth[1]) colWidth[1] = entry.version.length();
                    if (entry.homepage.length() > colWidth[2]) colWidth[2] = entry.homepage.length();
                    if (entry.name.length() > colWidth[3]) colWidth[3] = entry.name.length();
                } else {
                    if (options == null)
                        options = Strings.getVimModelineTab(line.trim().substring(2).trim());
                }
                entries.add(entry);
            }
            if (!containsVersionConvar) return; //don't rewrite if we didn't change anything
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(pucfile, StandardOpenOption.TRUNCATE_EXISTING)))) {
                for (PUCEntry entry : entries) {
                    bw.write(entry.toString(colWidth, options));
                    bw.newLine();
                }
                bw.flush();
            }
        });
    }

    //looks like csv with forced quotes, no escapes specified
    private static class PUCEntry {
        boolean isComment;
        String readValue;
        String convar,version,homepage,name;

        public static PUCEntry fromString(String line) {
            PUCEntry entry = new PUCEntry();
            entry.readValue = line;
            if (line.trim().startsWith("//")) {
                entry.isComment = true;
            } else {
                entry.isComment = false;

                int quote1 = line.indexOf('"');
                int quote2 = line.indexOf('"', quote1+1);
                if (quote1 < 0 || quote2 < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Unterminated quotes!");
                entry.convar = line.substring(quote1+1, quote2);

                int sep = line.indexOf(',',quote2+1);
                quote1 = line.indexOf('"',sep+1);
                quote2 = line.indexOf('"', quote1+1);
                if (sep < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Not enough columns");
                if (quote1 < 0 || quote2 < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Unterminated quotes!");
                entry.version = line.substring(quote1+1, quote2);

                sep = line.indexOf(',',quote2+1);
                quote1 = line.indexOf('"',sep+1);
                quote2 = line.indexOf('"', quote1+1);
                if (sep < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Not enough columns");
                if (quote1 < 0 || quote2 < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Unterminated quotes!");
                entry.homepage = line.substring(quote1+1, quote2);

                sep = line.indexOf(',',quote2+1);
                quote1 = line.indexOf('"',sep+1);
                quote2 = line.indexOf('"', quote1+1);
                if (sep < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Not enough columns");
                if (quote1 < 0 || quote2 < 0) throw new IllegalArgumentException("File is not a PUC Index or corrupted: Unterminated quotes!");
                entry.name = line.substring(quote1+1, quote2);

                if (quote2+1 < line.length() || !line.substring(quote2+1).trim().isEmpty())
                    throw new IllegalArgumentException("File is not a PUC Index or corrupted: Input line too long");
            }
            return entry;
        }

        public String toString(int[] columnWidths, @NotNull Strings.ModelineOptions options) {
            if (isComment) return readValue;
            return '"' + convar + "\", " +
                    Strings.getSpacingFromModelineOptions(convar.length(), columnWidths[0], options) +
                    '"' + version + "\", " +
                    Strings.getSpacingFromModelineOptions(version.length(), columnWidths[1], options) +
                    '"' + homepage + "\", " +
                    Strings.getSpacingFromModelineOptions(homepage.length(), columnWidths[2], options) +
                    '"' + name + '"';
        }
    }
}
