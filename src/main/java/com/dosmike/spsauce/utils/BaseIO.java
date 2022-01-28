package com.dosmike.spsauce.utils;

import com.dosmike.spsauce.Executable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Web Utils are in a separate class, so we can load it before we have all required dependencies loaded, to fetch them from the web
 */
public class BaseIO {

    //region web stuff
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
    //enregion

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
    //endregion

}
