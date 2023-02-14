package com.dosmike.spsauce.utils;

import com.dosmike.spsauce.Executable;
import com.dosmike.spsauce.am.SourceCluster;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class ArchiveIO {

    //region zipUtils
    public static Function<Path, Optional<Path>> SOURCEMOD_ARCHIVE_ROOT = path->Optional.of(path).filter(p->p.endsWith("addons"));

    public static Function<Path, Optional<Path>> SOURCEMOD_PLUGIN_PATH = path->{
        if (path.endsWith("sourcemod"))
            return Optional.of(path);
        else if (path.endsWith("scripting") ||
                path.endsWith("translations") ||
                path.endsWith("gamedata") ||
                path.endsWith("plugins") )
            return Optional.ofNullable(path.getParent());
        else
            return Optional.empty();
    };

    public static Path findArchiveRoot(Iterable<? extends ArchiveEntry> archive, Function<Path, Optional<Path>> mapRoot) throws IOException {
        Path shortest = null;
        for (ArchiveEntry entry : archive) {
            Path path = entryNameToPath(entry.getName());
            if (entry.isDirectory()) {
                path = mapRoot.apply(path).orElse(null);
                if (path != null && (shortest == null || shortest.getNameCount() > path.getNameCount()))
                    shortest = path;
            } else {
                while (path.getNameCount() > 1) {
                    path = path.getParent();
                    Path testPath = mapRoot.apply(path).orElse(null);
                    if (testPath != null) {
                        if (shortest == null || shortest.getNameCount() > testPath.getNameCount())
                            shortest = testPath;
                        break;
                    }
                }
            }
        }
//        if (shortest == null) throw new IOException("Could not locate root directory in archive");
        return shortest;
    }
    private static class ArchiveEntryIterator implements Iterator<ArchiveEntry> {
        private final ArchiveInputStream archive;
        private ArchiveEntry peek=null;
        private boolean isAtEnd=false;
        private ArchiveEntryIterator(ArchiveInputStream archive) {
            this.archive = archive;
            peekNext();
        }
        @Override
        public boolean hasNext() {
            return !isAtEnd;
        }
        private void peekNext() {
            try {
                peek = archive.getNextEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (peek == null) isAtEnd = true;
        }
        @Override
        public ArchiveEntry next() {
            if (isAtEnd) throw new NoSuchElementException();
            ArchiveEntry n = peek;
            peekNext();
            return n;
        }
    };
    private static Iterable<ArchiveEntry> getArchiveEntries(final ArchiveInputStream archive) {
        return () -> new ArchiveEntryIterator(archive); //whack wrapper
    }
    private static Path entryNameToPath(String string) {
        if (string.charAt(0)=='/'||string.charAt(0)=='\\') string = string.substring(1);
        String[] parts = string.split("[/\\\\]");
        if (parts.length <= 1) {
            return Paths.get(parts[0]);
        } else {
            String cat = parts[0];
            String[] cdr = Arrays.copyOfRange(parts, 1, parts.length);
            return Paths.get(cat,cdr);
        }
    }
    public interface ArchiveOpener { ArchiveInputStream open(Path path) throws IOException; }
    public static boolean FileExtractFilter(Path p) {
        String fname = p.getFileName().toString();
        if (!fname.contains(".") || fname.endsWith(".")) return false; //whack files we don't care about
        if (fname.endsWith(".sp") || fname.endsWith(".inc")) return true; //allow no name for these (download will use remote name)
        return ArgParser.IsFlagSet(Executable.fExtractAll) && !fname.startsWith("."); //extract misc file if not hidden (like .gitignore)
    }
    //endregion

    //region unpack structured
    /**
     * creates directories and a file in target\relativePath where relative path is entryPath relative to rootPath
     * @param entryPath full archive path / out file relative to target
     * @param rootPath path in archive to relativize against (strip start) or null
     * @param target the directory to append thepath to
     */
    private static OutputStream unpackGetOutputStream(Path entryPath, Path rootPath, Path target) throws IOException {
        System.out.println("Extracting "+entryPath.toString());

        Path entryRelative = rootPath != null ? rootPath.relativize(entryPath) : entryPath;
        Path extractTo = target.resolve(entryRelative);
        BaseIO.MakeDirectories(target, entryRelative.getParent());

        return Files.newOutputStream(extractTo, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    public static int Unpack(Path archive, Path target, Function<Path, Optional<Path>> rootFinder, @Nullable Predicate<Path> fileFilter) throws IOException {
        archive = archive.toAbsolutePath().normalize();
        target = target.toAbsolutePath().normalize();
        String fileName = archive.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".7z")) {
            return unpackSevenZip(archive, target, rootFinder, fileFilter);
        } else if (fileName.endsWith(".tar.gz")) {
            return unpackArchive(archive, target, rootFinder, fileFilter, p->new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(p)))) );
        } else if (fileName.endsWith(".zip")) {
            return unpackArchive(archive, target, rootFinder, fileFilter, p->new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(p))) );
        }
        int x = fileName.indexOf('.');
        if (x>=0 && x < fileName.length()-1) {
            fileName = fileName.substring(x+1);
            throw new IOException("Unsupported archive type: "+fileName);
        } else
            throw new IOException("Unsupported archive type");
    }
    /** @returns amount of files unpacked, 0 is probably an error */
    private static int unpackSevenZip(Path archive, Path target, Function<Path, Optional<Path>> rootFinder, @Nullable Predicate<Path> fileFilter) throws IOException {
        SevenZFile file = new SevenZFile(archive.toFile());
        SevenZArchiveEntry entry;
        Path entryPath, rootPath = findArchiveRoot(file.getEntries(), rootFinder);
        if (rootPath == null) return 0;
        int extracted = 0;
        while ((entry = file.getNextEntry()) != null) {
            entryPath = entryNameToPath(entry.getName());
            if (entryPath.startsWith(rootPath) && !entry.isDirectory() && entry.hasStream()) {
                if (fileFilter != null && !fileFilter.test(entryPath)) continue; //whack files we don't care about
                // unpack to root path parent as we unpack into that directory name, not just the contents of it
                try (OutputStream outstream = unpackGetOutputStream(entryPath, rootPath, target)) {
                    ChunckReadable.copyStream(ChunckReadable.chunks(file, entry.getSize()), outstream);
                    outstream.flush();
                }
                extracted++;
            }
        }
        return extracted;
    }
    private static int unpackArchive(Path file, Path target, Function<Path, Optional<Path>> rootFinder, @Nullable Predicate<Path> fileFilter, ArchiveOpener opener) throws IOException {
        Path rootPath, entryPath;
        int extracted = 0;
        try (ArchiveInputStream zis = opener.open(file)) {
            rootPath = findArchiveRoot(getArchiveEntries(zis), rootFinder);
        }
        if (rootPath == null) return 0;
        //we now need to re-open the stream to go back to the beginning of the file
        try (ArchiveInputStream zis = opener.open(file)) {
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry())!=null) {
                entryPath = entryNameToPath(entry.getName());
                if (entryPath.startsWith(rootPath) && !entry.isDirectory()) {
                    if (fileFilter != null && !fileFilter.test(entryPath)) continue; //whack files we don't care about
                    try (OutputStream outstream = unpackGetOutputStream(entryPath, rootPath, target)) {
                        ChunckReadable.copyStream(ChunckReadable.chunks(zis), outstream);
                        outstream.flush();
                    }
                    extracted++;
                }
            }
        }
        return extracted;
    }
    //endregion

    //region unpack loose
    private static String[] smdirs = new String[] {"configs","extensions","gamedata","plugins","scripting","translations"};
    private static int pathContains(Path needle, Path haystack) {
        int lst = haystack.getNameCount() - needle.getNameCount();
        for (int i=0; i<=lst; i++) if (haystack.subpath(i,i+needle.getNameCount()).equals(needle)) return i;
        return -1;
    }
    /** only directories please */
    private static boolean checkSMFolder(Path dir) {
        if (dir.getNameCount() < 1) return false;
        return Arrays.stream(smdirs).anyMatch(sub->pathContains(Paths.get("sourcemod",sub),dir)>=0 || dir.startsWith(sub));
    }
    /** guess what the sourcemod folder is within the archive or null
     * will also pick folders if file ends with a .sp or .smx in a directory that's not scripting/plugins */
    private static Path estimateSMRoot(Path file) {
        int subAt = Arrays.stream(smdirs)
                .map(sub->pathContains(Paths.get("sourcemod",sub),file))
                .filter(x->x>=0).min(Integer::compare).orElse(-1);
        if (subAt<0) //try again without sourcemod parent
            subAt = Arrays.stream(smdirs)
                    .map(sub->pathContains(Paths.get(sub),file))
                    .filter(x->x>=0).min(Integer::compare).orElse(-1);
        if (subAt<0 && (file.getFileName().toString().endsWith(".sp") || file.getFileName().toString().endsWith(".smx")))
            return file.getNameCount() > 1 ? file.getParent() : null;
        return (subAt>0) ? file.subpath(0, subAt) : null;
    }
    /**
     * input should start with addons/sourcemod
     * estimate where to unpack the file to within spcache (returns relative)
     * returns null if unsure
     */
    private static Path estimateSMFolder(Path file, Path rootGuess) {
        Path toGuess = SourceCluster.EstimateDirectoryByName(file.getFileName().toString().toLowerCase());
        if (rootGuess == null) {
            rootGuess = toGuess;
            if (rootGuess == null) return null;
            return rootGuess.resolve(file);
        }
        Path relative = rootGuess.relativize(file);//without root

        Path search = Paths.get("addons","sourcemod");
        if (relative.startsWith(search)) return relative;
        search = Paths.get("sourcemod");
        if (relative.startsWith(search)) return Paths.get("addons").resolve(relative);
        if (toGuess != null) {
            toGuess = toGuess.getParent();
            //resolve overlap in paths
            int max = Math.min(toGuess.getNameCount(),relative.getNameCount());
            int overlap=0;
            for (int i=1;i<max;i++) {
                Path relStart = relative.subpath(0,i);
                if (toGuess.endsWith(relStart)) overlap = i;
            }
            if (overlap>0) {
                toGuess = toGuess.subpath(0,toGuess.getNameCount()-overlap);
            }
            //stitch
            return toGuess.resolve(relative);
        }
        return Paths.get("addons","sourcemod").resolve(relative);
    }
    /** filter used to figure out archive structure */
    private static boolean archiveCountWhitelist(String filename) {
        filename = filename.toLowerCase();
        if (filename.startsWith(".") || filename.endsWith(".")) return false; //ignore files
        if (filename.startsWith("readme.") || filename.startsWith("license")) return false; //project info
        if (filename.endsWith(".exe") || !filename.contains(".")) return true; //windows/unix executabled
        if (filename.endsWith(".sp") || filename.endsWith(".inc")) return true; //script source
        if (filename.endsWith(".smx") || filename.endsWith(".dll") || filename.endsWith(".so")) return true; //libraries
        if (filename.endsWith(".txt") || filename.endsWith(".cfg")) return true; //other configs
        return false;
    }
    /** find path with most addons/sourcemod/x entries, null means root*/
    private static Path mostFittingForRoot(Iterable<? extends ArchiveEntry> entries, Ref<Boolean> typesAreMixed, Ref<Boolean> includeOnly) {
        Map<Path,Integer> fm = new HashMap<>();
        Map<Path,Set<String>> fir = new HashMap<>(); //counts file types in root paths
        int useRoot=0;
        Set<String> rootTypes = new HashSet<>();
        typesAreMixed.it = false;
        for (ArchiveEntry e : entries) {
            if (e.isDirectory()) continue;
            Path ep = entryNameToPath(e.getName());
            if (!archiveCountWhitelist(ep.getFileName().toString())) continue;
            Path rg = estimateSMRoot(ep), ext = SourceCluster.EstimateDirectoryByName(e.getName());
            String fext = ep.getFileName().getFileName().toString();
            if (fext.contains(".") && !fext.endsWith(".")) {
                fext = fext.substring(fext.lastIndexOf('.') + 1);
            } else fext = "";
            if (rg!=null) {
                fm.merge(rg, 1, Integer::sum);
                fir.computeIfAbsent(ep.getParent(), k->new HashSet<>()).add(fext);
            } else if (ext != null) {
                typesAreMixed.it=true; //directories are whack
                fm.merge(ep.getParent(), 1, Integer::sum);
                fir.computeIfAbsent(ep.getParent(), k->new HashSet<>()).add(fext);
            } else if (ep.getNameCount()==1) {
                useRoot++;
                fir.computeIfAbsent(ep.getParent(), k->new HashSet<>()).add(fext);
            }
        }
        Path best=null; int bi=0;
        for (Map.Entry<Path, Integer> e : fm.entrySet()) if (e.getValue() > bi) {
            best = e.getKey(); bi = e.getValue();
        }

        includeOnly.it = true;
        if (best != null && bi > useRoot) {
            for (Map.Entry<Path, Set<String>> p : fir.entrySet()) {
                if (!p.getKey().startsWith(best)) continue;
                if (p.getValue().size() > 1 || (p.getValue().size()==1 && !p.getValue().contains("inc")))
                    includeOnly.it = false;
                if (p.getValue().stream().map(String::toLowerCase).filter(x->x.equals("sp")||x.equals("smx")||x.equals("inc")).count()>1)
                    typesAreMixed.it = true;
            }
        }
        if (includeOnly.it) typesAreMixed.it = false; //previous assumption on missing directories is wrong
//        else if (useRoot > 0) {
//            if (rootTypes.size()>2) typesAreMixed.it = true;
//        }
        return bi > useRoot ? best : null;
    }
    public static int UnpackUnordered(Path archive, Path target, @Nullable Predicate<Path> fileFilter) throws IOException {
        archive = archive.toAbsolutePath().normalize();
        target = target.toAbsolutePath().normalize();
        String fileName = archive.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".7z")) {
            return unpackSevenZipSmart(archive, target, fileFilter);
        } else if (fileName.endsWith(".tar.gz")) {
            return unpackSmart(archive, target, fileFilter, p->new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(p)))) );
        } else if (fileName.endsWith(".zip")) {
            return unpackSmart(archive, target, fileFilter, p->new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(p))) );
        }
        int x = fileName.indexOf('.');
        if (x>=0 && x < fileName.length()-1) {
            fileName = fileName.substring(x+1);
            throw new IOException("Unsupported archive type: "+fileName);
        } else
            throw new IOException("Unsupported archive type");
    }
    private static int unpackSevenZipSmart(Path archive, Path target, @Nullable Predicate<Path> fileFilter) throws IOException {
        SevenZFile file = new SevenZFile(archive.toFile());
        SevenZArchiveEntry entry;
        Ref<Boolean> mixedContent = new Ref<>(), includeOnly = new Ref<>();
        Path entryPath, pathGuess, rootGuess = mostFittingForRoot(file.getEntries(), mixedContent, includeOnly);
        int extracted = 0;
        while ((entry = file.getNextEntry()) != null) {
            if (entry.isDirectory() || !entry.hasStream()) continue;
            entryPath = entryNameToPath(entry.getName());
            if (rootGuess == null || mixedContent.it) pathGuess = SourceCluster.EstimateDirectoryByName(entryPath.getFileName().toString());
            else pathGuess = estimateSMFolder(entryPath, rootGuess);

            if (pathGuess != null) {
                if (fileFilter != null && !fileFilter.test(entryPath)) continue; //whack files we don't care about
                // unpack to root path parent as we unpack into that directory name, not just the contents of it
                try (OutputStream outstream = unpackGetOutputStream(pathGuess, null, target)) {
                    ChunckReadable.copyStream(ChunckReadable.chunks(file, entry.getSize()), outstream);
                    outstream.flush();
                }
                extracted++;
            }
        }
        return extracted;
    }
    private static int unpackSmart(Path file, Path target, @Nullable Predicate<Path> fileFilter, ArchiveOpener opener) throws IOException {
        Path entryPath, pathGuess, rootGuess;
        Ref<Boolean> mixedContent = new Ref<>(), includeOnly = new Ref<>();
        try (ArchiveInputStream tais = opener.open(file)) {
            rootGuess = mostFittingForRoot(getArchiveEntries(tais), mixedContent, includeOnly);
        }
        int extracted = 0;
        //we now need to re-open the stream to go back to the beginning of the file
        try (ArchiveInputStream zis = opener.open(file)) {
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry())!=null) {
                if (entry.isDirectory()) continue;
                entryPath = entryNameToPath(entry.getName());
                if (rootGuess == null || mixedContent.it) pathGuess = SourceCluster.EstimateDirectoryByName(entryPath.getFileName().toString());
                else pathGuess = estimateSMFolder(entryPath, rootGuess);

                if (pathGuess != null && !entry.isDirectory()) {
                    if (fileFilter != null && !fileFilter.test(entryPath)) continue; //whack files we don't care about
                    try (OutputStream outstream = unpackGetOutputStream(pathGuess, null, target)) {
                        ChunckReadable.copyStream(ChunckReadable.chunks(zis), outstream);
                        outstream.flush();
                    }
                    extracted++;
                }
            }
        }
        return extracted;
    }
    //endregion
}
