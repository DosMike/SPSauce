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
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InOut {

    //region WebCon
    public static HttpURLConnection PrepareConnection(String target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) (new URL(target).openConnection());
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SPSauce/1.0 (by reBane aka DosMike)");
        connection.setInstanceFollowRedirects(true);
        connection.setDoInput(true);
        return connection;
    }
    public static void CheckHTTPCode(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 400) {
            try (InputStream in = connection.getErrorStream()) {
                ChunckReadable.copyStream(ChunckReadable.chunks(in), System.err);
            } catch (IOException e) {/**/}
            throw new IOException("Connection refused: " + connection.getResponseCode() + " " + connection.getResponseMessage());
        }
    }

    public static String ContentDispositionFilename(String contentDisposition) {
        return Arrays.stream(contentDisposition.split(";"))
                .filter(v -> v.toLowerCase().contains("filename")).map(v -> {
                    v = v.trim();
                    boolean enc = (v.contains("*="));
                    v = v.substring(v.indexOf('=') + 1);
                    try {
                        if (enc) {
                            String e, l;
                            int i = v.indexOf('\'');
                            e = v.substring(0, i);
                            v = v.substring(i + 1);
                            i = v.indexOf('\'');
                            l = v.substring(0, i);
                            v = v.substring(i + 1);
                            v = URLDecoder.decode(v, e);
                        } else {
                            if (v.charAt(0) == '"') v = v.substring(1, v.length() - 1);//strip quotes
                            v = URLDecoder.decode(v, "UTF-8");
                        }
                    } catch (Throwable x) {
                        return null;
                    }
                    return v;
                }).filter(Objects::nonNull).findFirst().orElseThrow(()->new IllegalArgumentException("Could not get filename"));
    }
    public static String DownloadURL(String url, Path target, String hashMethod, @Nullable Ref<String> filename) throws IOException {
        HttpURLConnection connection = PrepareConnection(url);
        CheckHTTPCode(connection);
        if (target.getFileName().toString().startsWith(".")) {
            Map<String, List<String>> headers = connection.getHeaderFields();
            String contentdisposition = headers.keySet().stream()
                    .filter("Content-Disposition"::equalsIgnoreCase).findFirst()
                    .orElseThrow(()->new IOException("No content disposition: Probably means auto compilation for plugin failed"));
            String suggestedFilename = ContentDispositionFilename(String.join(";",connection.getHeaderFields().get(contentdisposition)));
            target = target.getParent().resolve(suggestedFilename);
        }
        if (filename!=null) filename.it = target.getFileName().toString();
        String hash = StreamToFile(ChunckReadable.chunks(connection.getInputStream()), target, hashMethod);
        connection.disconnect();
        return hash;
    }
    public static String StreamToFile(ChunckReadable source, Path target, String hashMethod) throws IOException {
        System.out.println("Downloading to "+target);
        OutputStream outstream = Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        String hash=null;
        if (hashMethod!=null) hash = ChunckReadable.copyStreamAndHash(source, outstream, hashMethod);
        else ChunckReadable.copyStream(source, outstream);
        return hash;
    }
    //endregion

    //region FileSystem
    public static boolean ValidateFile(Path file, String hashMethod, String hashString) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(hashMethod);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[4096];
        int read = 0;
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            while ((read = in.read(buffer))>=0) {
                md.update(buffer,0,read);
            }
            byte[] hashData = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashData) sb.append(String.format("%02X", b));
            return hashString.equalsIgnoreCase(sb.toString());
        }
    }
    public static void MakeDirectories(Path base, Path then) throws IOException {
        base = base.toAbsolutePath().normalize();
        Path target = base.resolve(then).toAbsolutePath().normalize();
        if (!target.startsWith(base)) throw new IOException("Illegal target directory, is walking outside the base");
        if (!Files.isDirectory(then)) Files.createDirectories(target);
        File dir = target.toFile(); //windows will fuck up created directories otherwise, using File because Files will only do POSIX
        if (!dir.canExecute() && !dir.setExecutable(true,false)) throw new IOException("Failed to set directory executable");
        if (!dir.canRead() && !dir.setReadable(true,false)) throw new IOException("Failed to set directory readable");
        if (!dir.canWrite() && !dir.setWritable(true,false)) throw new IOException("Failed to set directory writeable");
        if (!dir.canRead() || !dir.canWrite() || !dir.canExecute()) throw new IOException("Permissions are f***ed up");
    }
    public static void RemoveRecursive(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(Executable.workdir)) throw new IOException("Illegal target directory, can not delete directories outside work dir");
        if (Files.isDirectory(path)) {
            Collection<Path> children = Files.list(path).collect(Collectors.toList());
            for (Path child : children) RemoveRecursive(child);
        }
        Files.deleteIfExists(path);
    }
    public enum ReplaceFlag {
        All,
        Older,
        Skip,
        Error
    }
    /**
     * Moves or Copies files from <tt>from</tt> to <tt>to</tt>. When moving it tries a shortcut for renaming, otherwise
     * the file tree will be walked.
     * @param from file or directory to copy
     * @param to name of the target file or directory
     * @param move if true files will be moved, otherwise copied
     * @param replace can limit what files are copied
     */
    public static void MoveFiles(Path from, Path to, boolean move, ReplaceFlag replace) throws IOException {
        System.out.println((move?"Move":"Copy")+" files...");
        System.out.println("Source: "+from);
        System.out.println("Target: "+to);
        from = from.toAbsolutePath().normalize();
        to = to.toAbsolutePath().normalize();
        if (!from.startsWith(Executable.workdir)) throw new IOException("Illegal source directory, can not move directories outside work dir");
        if (!to.startsWith(Executable.workdir)) throw new IOException("Illegal target directory, can not move directories outside work dir");
        if (to.startsWith(from)) throw new IOException("Target cannot be a sub directory of source");
        if (move) try { //shortcut for renaming
            Files.move(from, to);
            return; //was able to move file / rename directory
        } catch (IOException ignore) {}
        final Path source = from, target = to;
        Files.walkFileTree(from, new FileVisitor<Path>() {
            String prefix = " ";
            final LinkedList<Long> remainingStack = new LinkedList<>();
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (remainingStack.isEmpty()) {
                    System.out.println(prefix + dir.getFileName().toString());
                } else {
                    long remain = remainingStack.peek()-1L;
                    remainingStack.set(0,remain);
                    if (remain>0L) {
                        System.out.println(prefix + "+-" + dir.getFileName().toString());
                        prefix += "| ";
                    } else {
                        System.out.println(prefix + "`-" + dir.getFileName().toString());
                        prefix += "  ";
                    }
                }
                remainingStack.push(Files.list(dir).count());

                MakeDirectories(target, source.relativize(dir));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!remainingStack.isEmpty()) { //will be empty for single files
                    long remain = remainingStack.peek() - 1L;
                    remainingStack.set(0, remain);
                    if (remain > 0L) {
                        System.out.println(prefix + "+-" + file.getFileName().toString());
                    } else {
                        System.out.println(prefix + "`-" + file.getFileName().toString());
                        if (prefix.length()>=2)
                            prefix = prefix.substring(0, prefix.length() - 2) + "  ";
                    }
                }

                Path destination = target.resolve(source.relativize(file));
                Set<CopyOption> fCopy = new HashSet<>();
                fCopy.add(StandardCopyOption.COPY_ATTRIBUTES);
                fCopy.add(LinkOption.NOFOLLOW_LINKS);
                if (Files.exists(destination)) {
                    //skip if specified or file is older than destination
                    if (replace == ReplaceFlag.Skip || (replace == ReplaceFlag.Older &&
                            Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(destination)) < 0
                            )) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (replace != ReplaceFlag.Error) {
                        fCopy.add(StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.copy(file, destination, fCopy.toArray(new CopyOption[0]));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                exc.printStackTrace();
                return FileVisitResult.TERMINATE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (prefix.length()>2) prefix = prefix.substring(0,prefix.length()-2); else prefix = "";
                remainingStack.pop();

                if (move) RemoveRecursive(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    //endregion

    //region zipUtils
    public static Predicate<Path> SOURCEMOD_ARCHIVE_ROOT = path-> path.endsWith(Paths.get("addons"));

    public static Path findArchiveRoot(Iterable<? extends ArchiveEntry> archive, Predicate<Path> isRoot) throws IOException {
        Path shortest = null;
        for (ArchiveEntry entry : archive) {
            Path path = entryNameToPath(entry.getName());
            if (entry.isDirectory()) {
                if (isRoot.test(path) && (shortest == null || shortest.getNameCount() > path.getNameCount()))
                    shortest = path;
            } else {
                while (path.getNameCount() > 1) {
                    path = path.getParent();
                    if (isRoot.test(path)) {
                        if (shortest == null || shortest.getNameCount() > path.getNameCount())
                            shortest = path;
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
        if (fname.isEmpty() || fname.startsWith(".") || !fname.contains(".") || fname.endsWith(".")) return false; //whack files we don't care about
        return  (ArgParser.IsFlagSet(Executable.fExtractAll) || fname.endsWith(".sp") || fname.endsWith(".inc"));
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
        MakeDirectories(target, entryRelative.getParent());

        return Files.newOutputStream(extractTo, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    public static int Unpack(Path archive, Path target, Predicate<Path> rootFinder, @Nullable Predicate<Path> fileFilter) throws IOException {
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
    private static int unpackSevenZip(Path archive, Path target, Predicate<Path> rootFinder, @Nullable Predicate<Path> fileFilter) throws IOException {
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
                try (OutputStream outstream = unpackGetOutputStream(entryPath, rootPath.getParent(), target)) {
                    ChunckReadable.copyStream(ChunckReadable.chunks(file, entry.getSize()), outstream);
                    outstream.flush();
                }
                extracted++;
            }
        }
        return extracted;
    }
    private static int unpackArchive(Path file, Path target, Predicate<Path> rootFinder, @Nullable Predicate<Path> fileFilter, ArchiveOpener opener) throws IOException {
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
                    try (OutputStream outstream = unpackGetOutputStream(entryPath, rootPath.getParent(), target)) {
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
