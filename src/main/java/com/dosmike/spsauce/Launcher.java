package com.dosmike.spsauce;

import com.dosmike.spsauce.utils.BaseIO;
import com.dosmike.spsauce.utils.ChunckReadable;
import com.dosmike.spsauce.utils.Ref;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * so, I'm not giving you apache2 libraries. you're downloading them
 * I'm not doing this in spite of the apache license, I'm just not a fan of
 * packaging other projects source code with my source code when it's managed by
 * some VCS like maven. Just bloats the project, and in the scope of this tool
 * why should your SourcePawn repo contain the source of Apache Compress just because
 * you uploaded this build tool?
 * So with this, only adding this build tool's jar to your repo is still compliant as you're not
 * distributing apache licensed binaries. if the user running this tool does not have the libraries
 * they are fetched from a license compliant source and git ignored, so the user won't accidentally
 * infringe copyright.
 * If a user decides to package the libraries in their repo as well they'd have to add the sources,
 * but at that point I just hope they know what they're doing.
 * Again: This is not to discredit work licensed under the Apache2 license, but to keep repos clean!
 *
 * download links from maven.org seem to be
 * https://repo1.maven.org/maven2/&lt;group/reverse>/&lt;name>/&lt;version>/&lt;name>-&lt;version>.jar
 */
public class Launcher {

    static {
        init(); //bypass static init code size limit
    }
    static void init() {
        URL mysaucecode = Launcher.class.getProtectionDomain().getCodeSource().getLocation();
        if (!mysaucecode.getProtocol().equals("file")) throw new RuntimeException("SPSauce does not support running from non-file sources");
        try {
            binaryDir = Paths.get(mysaucecode.toURI()).toAbsolutePath();
            if (binaryDir.getFileName().toString().endsWith(".jar")) {
                //we are packed, libs are not within the jar tho
                selfArchive = binaryDir.getFileName().toString();
                binaryDir = binaryDir.getParent();
            } else {
                //we are not packed! binaryDir is classpath
                selfArchive = "";
                binaryDir = Paths.get(".").toAbsolutePath(); //use work dir for debugging
            }
            BaseIO.MakeDirectories(binaryDir, Paths.get("libs"));
            binaryDir = binaryDir.resolve("libs");
            Path tmp = binaryDir.resolve(".gitignore");
            if (!Files.exists(tmp) || !Files.isRegularFile(tmp)) {
                Files.write(tmp, "# This file was generated automatically\n# Please only publish libraries with your repo if you know what you're doing\n\n**".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            tmp = binaryDir.resolve("LICENSE");
            if (!Files.exists(tmp) || !Files.isRegularFile(tmp)) {
                ChunckReadable.copyStream(ChunckReadable.chunks(Launcher.class.getClassLoader().getResourceAsStream("LICENSE")), Files.newOutputStream(tmp, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not determine local directory for SPSauce");
        } catch (IOException e) {
            throw new RuntimeException("Could not prepare local library directory");
        }
    }

    static class LibraryClassLoader extends URLClassLoader {
        private LibraryClassLoader() {
            super(new URL[]{Launcher.class.getProtectionDomain().getCodeSource().getLocation()}, Launcher.class.getClassLoader());
        }
        void addPath(Path path) {
            try {
                path = path.toAbsolutePath();
                if (!path.startsWith(binaryDir)) throw new RuntimeException("Library would not locate into the local library directory");
                addURL(path.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Could not validate library path!");
            }
        }
    }

    private static Path binaryDir;
    private static String selfArchive;
    private static final LibraryClassLoader loader = new LibraryClassLoader();

    public static void LoadMavenDep(String group, String name, String version) throws IOException {
        if (!group.matches("^[\\w.-]+$")) throw new IllegalArgumentException("Invalid group");
        if (!name.matches("^[\\w-]+$")) throw new IllegalArgumentException("Invalid artifact");
        if (!version.matches("^[\\w.-]+$")) throw new IllegalArgumentException("Invalid version");
        Path localLib = binaryDir.resolve(name+"-"+version+".jar");
        if (!Files.exists(localLib)) {
            String url = "https://repo1.maven.org/maven2/"+group.replace('.','/')+"/"+name+"/maven-metadata.xml";
            System.out.println("Fetching dependency information for "+group+":"+name+":"+version+" ...");
            System.out.println("< "+url);
            String meta = BaseIO.DownloadURLtoMemory(url);
            if (version.equalsIgnoreCase("latest")) {
                Pattern latest = Pattern.compile("<latest>([\\w.-]+)</latest>");
                Matcher m = latest.matcher(meta);
                if (m.find()) {
                    version = m.group(1);
                } else throw new IOException("Could not locate latest artifact for "+group+":"+name);
            } else {
                if (!meta.contains("<version>"+version+"</version>")) {
                    throw new IOException("Could not locate specified artifact "+group+":"+name+":"+version);
                }
            }
            System.out.println("Located Library "+group+":"+name+":"+version);
            Ref<String> artifactname = new Ref<>();
            String baseName = name+"-"+version;
            String hash;
            String baseUrl = "https://repo1.maven.org/maven2/"+group.replace('.','/')+"/"+name+"/"+version+"/"+baseName;

            url = baseUrl+".jar";
            System.out.println("< "+url);
            BaseIO.DownloadURL(url,binaryDir.resolve(baseName+".jar"),null,artifactname);
            if (!artifactname.it.equals(localLib.getFileName().toString()))
                BaseIO.MoveFiles(binaryDir.resolve(artifactname.it), localLib, true, BaseIO.ReplaceFlag.Older);

            url = baseUrl+"-sources.jar";
            System.out.println("< "+url);
            BaseIO.DownloadURL(url,binaryDir.resolve(baseName+"-sources.jar"),null,null);
        }
        loader.addPath(binaryDir.resolve(localLib));
//        System.out.println("Loaded library "+group+":"+name+" from "+localLib.getFileName().toString());
    }

    public static void LoadBakedDeps() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ChunckReadable.copyStream(ChunckReadable.chunks(Launcher.class.getClassLoader().getResourceAsStream(".runtimedeps")), baos);
            String[] deps = baos.toString().split("\n");
            for (String dep : deps) {
                if (dep.isEmpty()) continue;
                String[] artifact = dep.split(":");
                LoadMavenDep(artifact[0], artifact[1], artifact[2]);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to load dependencies!", e);
        }
    }

    public static void Run(String mainClass, String[] args) {
        try {
            Class<?> executable = loader.loadClass(mainClass);
            Method main = executable.getDeclaredMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException t) {
            System.err.println("Failed to launch MainClass from injected Loader!");
            t.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Launcher.LoadBakedDeps();
        Launcher.Run("com.dosmike.spsauce.Executable", args);
    }

}
