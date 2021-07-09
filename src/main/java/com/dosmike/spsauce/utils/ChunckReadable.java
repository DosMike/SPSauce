package com.dosmike.spsauce.utils;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//region stream copy
public interface ChunckReadable {
    static ChunckReadable chunks(InputStream is) {
        assert !(is instanceof ArchiveInputStream);
        return new ChunckReadable() {
            @Override public int read(byte[] buffer, int offset, int size) throws IOException { return is.read(buffer,offset,size); }
            @Override public void close() throws IOException { is.close(); }
        };
    }

    static ChunckReadable chunks(ArchiveInputStream is) {
        return new ChunckReadable() {
            @Override public int read(byte[] buffer, int offset, int size) throws IOException { return is.read(buffer,offset,size); }
        };
    }

    static ChunckReadable chunks(SevenZFile sz, final long maxbytes) {
        return new ChunckReadable() {
            long read;
            @Override public int read(byte[] buffer, int offset, int size) throws IOException {
                if (maxbytes > 0) {
                    int remain = (int) (maxbytes - read);
                    if (remain < 1) return -1;
                    int batch = sz.read(buffer, offset, Math.min(size, remain));
                    read += batch;
                    return batch;
                } else {
                    return sz.read(buffer,offset,size);
                }
            }

        };
    }

    static void copyStream(ChunckReadable in, OutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        while ((read=in.read(buffer))>=0) {
            out.write(buffer,0,read);
        }
        out.flush();
//        in.close();
//        out.close();
    }

    static String copyStreamAndHash(ChunckReadable in, OutputStream out, String hashMethod) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(hashMethod);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[4096];
        int read;
        while ((read=in.read(buffer))>=0) {
            md.update(buffer,0,read);
            out.write(buffer,0,read);
        }
        out.flush();
//        in.close();
//        out.close();
        byte[] hashBytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    default int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    int read(byte[] buffer, int offset, int size) throws IOException;

    default void close() throws IOException {
    }
}
