package com.dosmike.spsauce.release;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.script.BuildScript;
import com.dosmike.spsauce.utils.BaseIO;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * This is a set of files that might POTENTIALLY exist at the end of the build process.
 * The list is collected in a templated state and fully resolved by a dependant action (For example, a release zip task).
 * Keep in mind that a FileSet cares about plugin files, not arbitrary files. If no SPFileType can be assigned, adding fails!
 */
public class FileSet {

    public static class Entry {
        boolean resolved;
        boolean exists;
        String path;
        BaseIO.SPFileType type;

        public Entry(Path file) throws IOException {
            file = file.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Symbolic links are not supported for packing");
            this.path = Executable.workdir.relativize(file).toString();
            this.type = BaseIO.SPFileType.from(this.path).orElse(null);
        }

        public String getProjectFile() {
            if (resolved) return path;
            path = BuildScript.injectRefs(path);
            exists = Files.exists(Executable.workdir.resolve(path));
            return path;
        }
        public Path getProjectPath() {
            if (resolved) return Executable.workdir.resolve(path);
            path = BuildScript.injectRefs(path);
            Path full = Executable.workdir.resolve(path);
            exists = Files.exists(full);
            return full;
        }
        public BaseIO.SPFileType getType() {
            return type;
        }
        public String getInstallFile() {
            return getInstallPath().toString();
        }
        public Path getInstallPath() {
            if (type == null)
                throw new RuntimeException("This file type is not supported for packing");
            Path local = Paths.get(path);
            Path defaultPath = Paths.get(type.getDefaultPath());
            Path installDir;
            if (local.getNameCount()==1)
                //local is only a filename in root, put it directly into the default directory
                installDir = defaultPath.resolve(local);
            else
                //we have an actual path in the pwd, try matching it
                installDir = BaseIO.resolveAgainst(Paths.get(type.getDefaultPath()), Paths.get(path));
            if (installDir == null)
                throw new RuntimeException("Could not resolve install directory for \""+path+"\" against \""+type.getDefaultPath()+"\"");
            return installDir;
        }
        public String getUpdaterFile() {
            return getUpdaterPath().toString();
        }
        private static final Path smprefix = Paths.get("addons","sourcemod");
        public Path getUpdaterPath() {
            Path installPath = getInstallPath();
            if (installPath.startsWith(smprefix))
                return Paths.get("Path_SM").resolve(installPath.subpath(2, installPath.getNameCount()));
            else
                return Paths.get("Path_Mod").resolve(installPath);
        }

        public boolean isValid() {
            if (!resolved) getProjectPath();
            return exists;
        }
        public boolean isSpFile() {
            return type != null;
        }
        public String getFileExt() {
            String filename = getProjectPath().getFileName().toString();
            int dot=filename.lastIndexOf('.');
            if (dot < 0) return "";
            else return filename.substring(dot+1);
        }
    }

    Set<Entry> candidates = new HashSet<>();

    public void addFile(String path) throws IOException {
        addFile(Paths.get(path));
    }
    public void addFile(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
                for (Path value : dir) {
                    if (!Files.isHidden(value) && !Files.isSymbolicLink(value))
                        addFile(value);
                }
            }
        } else {
            System.out.println("+ Collecting "+path);
            candidates.add(new Entry(path));
        }
    }

    public Collection<Entry> getCandidates() {
        return candidates;
    }
}
