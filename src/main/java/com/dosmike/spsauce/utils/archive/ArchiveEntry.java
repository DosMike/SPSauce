package com.dosmike.spsauce.utils.archive;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;

public class ArchiveEntry {

    String internalPath;
    private Path path;
    private boolean directory;

    public Path getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    private ArchiveEntry() {}
    public static ArchiveEntry from(ZipEntry entry) {
        ArchiveEntry ne = new ArchiveEntry();
        ne.path = Paths.get(ne.internalPath = entry.getName());
        ne.directory = entry.isDirectory();
        return ne;
    }

}
