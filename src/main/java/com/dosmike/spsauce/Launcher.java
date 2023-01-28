package com.dosmike.spsauce;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarFile;
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
 *
 * This class will not load any custom packages until all dependencies are downloaded and mounted.
 */
public class Launcher {

    static {
        init(); //bypass static init code size limit
    }
    private static void init() {
        URL mysaucecode = Launcher.class.getProtectionDomain().getCodeSource().getLocation();
        if (!mysaucecode.getProtocol().equals("file")) throw new RuntimeException("SPSauce does not support running from non-file sources");
        try {
            binaryDir = Paths.get(mysaucecode.toURI()).toAbsolutePath();
            if (binaryDir.getFileName().toString().endsWith(".jar")) {
                //we are packed, libs are not within the jar tho
                binaryDir = binaryDir.getParent();
            } else {
                //we are not packed! binaryDir is classpath
                binaryDir = Paths.get(".").toAbsolutePath(); //use work dir for debugging
            }
            binaryDir = binaryDir.resolve("libs");
            Files.createDirectories(binaryDir);
            Path tmp = binaryDir.resolve(".gitignore");
            if (!Files.exists(tmp) || !Files.isRegularFile(tmp)) {
                Files.write(tmp, "# This file was generated automatically\n# Please only publish libraries with your repo if you know what you're doing\n\n**".getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            tmp = binaryDir.resolve("LICENSE");
            if (!Files.exists(tmp) || !Files.isRegularFile(tmp)) {
                CopyStreamAndClose(Launcher.class.getClassLoader().getResourceAsStream("LICENSE"), Files.newOutputStream(tmp, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not determine local directory for SPSauce");
        } catch (IOException e) {
            throw new RuntimeException("Could not prepare local library directory");
        }
    }

    private static void CopyStreamAndClose(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        while ((read=in.read(buffer))>=0) out.write(buffer,0,read);
        out.flush(); in.close(); out.close();
    }
    private static String ReadStreamAndClose(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CopyStreamAndClose(in, buf);
        return buf.toString();
    }
    private static InputStream OpenURL(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) (new URL(url).openConnection());
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SPSauce Library Bootstrapper/1.0 (github.com/dosmike/spsauce)");
        connection.setInstanceFollowRedirects(true);
        connection.setDoInput(true);
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 400) {
            try (InputStream in = connection.getErrorStream()) {
                System.err.println(ReadStreamAndClose(in));
            } catch (IOException e) {/**/}
            throw new IOException("Connection refused: " + connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        return connection.getInputStream();
    }

    private static Path binaryDir;
    private static Instrumentation instrument;

    private static Path LoadMavenDep(String group, String name, String version) throws IOException {
        if (!group.matches("^[\\w.-]+$")) throw new IllegalArgumentException("Invalid group");
        if (!name.matches("^[\\w-]+$")) throw new IllegalArgumentException("Invalid artifact");
        if (!version.matches("^[\\w.-]+$")) throw new IllegalArgumentException("Invalid version");
        Path localLib = binaryDir.resolve(name+"-"+version+".jar");
        if (!Files.exists(localLib)) {
            String url = "https://repo1.maven.org/maven2/"+group.replace('.','/')+"/"+name+"/maven-metadata.xml";
            System.out.println("Fetching dependency information for "+group+":"+name+":"+version+" ...");
            System.out.println("< "+url);
            String meta = ReadStreamAndClose(OpenURL(url));
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
            System.out.println(" Located Library "+group+":"+name+":"+version);
            String baseName = name+"-"+version;
            String baseUrl = "https://repo1.maven.org/maven2/"+group.replace('.','/')+"/"+name+"/"+version+"/"+baseName;

            url = baseUrl+".jar";
            System.out.println(" < "+url);
            CopyStreamAndClose(OpenURL(url),Files.newOutputStream(binaryDir.resolve(baseName+".jar")));

            url = baseUrl+"-sources.jar";
            System.out.println(" < "+url);
            CopyStreamAndClose(OpenURL(url),Files.newOutputStream(binaryDir.resolve(baseName+"-sources.jar")));
        }
        return localLib;
//        System.out.println("Loaded library "+group+":"+name+" from "+localLib.getFileName().toString());
    }


    private static void LoadBakedDeps() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CopyStreamAndClose(Launcher.class.getClassLoader().getResourceAsStream(".runtimedeps"), baos);
            String[] deps = baos.toString().split("\n");
            for (String dep : deps) {
                if (dep.isEmpty()) continue;
                String[] artifact = dep.split(":");
                Path jar = LoadMavenDep(artifact[0], artifact[1], artifact[2]).toAbsolutePath();
                instrument.appendToSystemClassLoaderSearch(new JarFile(jar.toFile()));
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to load dependencies!", e);
        }
    }

    public static void agentmain(final String ignore, final Instrumentation instrumentation) {
        instrument = instrumentation;
        LoadBakedDeps();
    }

    public static Path getBinaryDir() {
        return binaryDir.getParent().normalize();
    }


}
