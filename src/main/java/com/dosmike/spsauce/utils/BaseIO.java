package com.dosmike.spsauce.utils;

import com.dosmike.spsauce.Executable;
import org.intellij.lang.annotations.Language;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Web Utils are in a separate class, so we can load it before we have all required dependencies loaded, to fetch them from the web
 */
public class BaseIO {

    //region web stuff
    public static HttpURLConnection PrepareConnection(String target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) (new URL(target).openConnection());
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", Executable.UserAgent);
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

    public static String DownloadURL(String url, Path target, String hashMethod, Ref<String> filename) throws IOException {
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

    public static String DownloadURLtoMemory(String url) throws IOException {
        HttpURLConnection connection = PrepareConnection(url);
        CheckHTTPCode(connection);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ChunckReadable.copyStream(ChunckReadable.chunks(connection.getInputStream()), baos);
        connection.disconnect();
        return baos.toString();
    }
    //endregion

    //region FileSystem

    public static String GetFileHash(Path file, String hashMethod) throws IOException {
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
            return sb.toString();
        }
    }
    public static boolean ValidateFile(Path file, String hashMethod, String hashString) throws IOException {
        return hashString.equalsIgnoreCase(GetFileHash(file, hashMethod));
    }

    public static String ReadFileContent(Path base, Path then) throws IOException {
        base = base.toAbsolutePath().normalize();
        Path target = base.resolve(then).toAbsolutePath().normalize();
        if (!target.startsWith(base)) throw new IOException("Illegal target directory, is walking outside the base");
        return String.join(System.lineSeparator(), Files.readAllLines(target));
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

    public enum ReplaceFlag {
        All,
        Older,
        Skip,
        Error
    }

    /** always returns a mime type. tries the default content probe first.
     * if that returns empty the file is peeked for 2048 bytes and checked for ascii chars.
     * if ascii returns text/plain otherwise application/octet-stream */
    public static String getMimeType(Path file) throws IOException {
        String mime = Files.probeContentType(file);
        if (mime == null) {
            byte[] peekBuffer = new byte[2048];
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
                int read = in.read(peekBuffer);
                //use octet stream if any of the peeked bytes appear to be non-ascii
                // since java bytes are signed and ascii never uses the high bit, non-ascii chars appear negative
                for (int i = 0; i < read; i++) if (peekBuffer[i] <= 0) return "application/octet-stream";
                return "text/plain";
            }
        } else return mime;
    }

    public static void MakeExecutable(Path path) throws SecurityException {
        boolean isExecutable;
        String absolutePath = path.toAbsolutePath().toString();
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (securityManager!=null) securityManager.checkExec(absolutePath);
            isExecutable = Files.isExecutable(path);
        } catch (SecurityException e) {
            isExecutable = false;
        }

        if (!isExecutable) {
            //maybe we just downloaded sourcemod? try to set the executable flag if we know how to
            if (Executable.OS == Executable.OperatingSystem.Linux) try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-x---"));
            } catch (IOException e) {
                System.err.println("Could not set owner executable flag on spcomp - Compilation will fail!");
            } else if (!path.toFile().setExecutable(true, false)) {
                System.err.println("Could not set owner executable flag on spcomp - Compilation will fail!");
            }
        }

        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager!=null) securityManager.checkExec(absolutePath);
    }

    //endregion

    //region SourcePawn

    /** categorize files interesting for plugins */
    public enum SPFileType {
        //default paths relative to mod root
        PluginSource("\\.sp$", "addons/sourcemod/scripting"),
        PluginInclude("\\.inc$", "addons/sourcemod/scripting/include"),
        Plugin("\\.smx$", "addons/sourcemod/plugins"),
        Translation("[\\\\/]?translations[\\\\/].*\\.txt$|\\.phrases\\.txt$", "addons/sourcemod/translations"),
        GameData("[\\\\/]?gamedata[\\\\/].*\\.txt$|\\.games\\.txt$", "addons/sourcemod/gamedata"),
        Extension("\\.ext(?:\\.\\w+)*\\.(?:dll|so)$", "addons/sourcemod/extensions"),
        PluginConfig("[\\\\/]?configs[\\\\/]", "addons/sourcemod/configs"),
        ModConfig("[\\\\/]?cfg[\\\\/]", "cfg"),
        Maps("\\.bsp$|\\.nav$", "maps"),
        Materials("\\.vtf$|\\.vmt$", "materials"),
        Models("\\.mdl$|\\.phy$|\\.(?:dx[89]0|sw)\\.vtx$|\\.vvd$", "models"),
        Sounds("\\.wav$|\\.mp3$", "sound"),
        ProjectMeta("license|readme\\.|\\.md$", ""),
        ;
        private final Predicate<String> filePattern;
        private final String defaultPath;
        SPFileType(@Language("RegExp") String filePattern, String defaultPath) {
            this.filePattern = Pattern.compile(filePattern, Pattern.CASE_INSENSITIVE).asPredicate();
            this.defaultPath = defaultPath;
        }

        public boolean matches(String filename) { return filePattern.test(filename); }
        public static Optional<SPFileType> from(String filename) {
            String[] parts = filename.split("[\\\\/]");
            if (parts.length == 0) return Optional.empty();
            for (SPFileType type : values()) {
                if (type.matches(parts[parts.length-1])) return Optional.of(type);
            }
            return Optional.empty();
        }
        public static Optional<SPFileType> from(Path path) {
            return from (path.toAbsolutePath().toString());
        }
        public String getDefaultPath() {
            return defaultPath;
        }
    }

    /**
     * Tries to fit the file to the directory path for non-overlapping root directories, but matching subdirectory tree
     * This means, find the largest possible end for directory that is equal to the beginning of file
     * @return the stitched path (file relative to directory) if possible, null otherwise
     */
    public static Path resolveAgainst(Path directory, Path file) {
        // addons/sourcemod/plugins/ -> sourcemod/plugins/ -> plugins/
        // plugins/myplugin.smx -> plugins/
        //so: pop front for directory, pop end for file
        int maxOverlap = Math.min(directory.getNameCount(), file.getNameCount()-1);
        if (maxOverlap <= 0) return null; //file name has no directory for further information
        for (int i=maxOverlap; i>0; i--) {
            int subdirFrom = directory.getNameCount()-i;
            Path subDir = directory.subpath(subdirFrom, directory.getNameCount());
            if (file.startsWith(subDir)) {
                return directory.subpath(0,subdirFrom).resolve(file);
            }
        }
        return null;
    }
    //endregion

}
