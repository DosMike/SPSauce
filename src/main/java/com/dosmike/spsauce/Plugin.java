package com.dosmike.spsauce;

import com.dosmike.spsauce.am.SourceCluster;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Plugin {

    public String name;
    public String version;
    public String game;
    public String homepage;

    public String packageurl = null; //zip url for addons/... package
    public SourceCluster amattachments = null; //raw file urls from am
    public Object downloadRef; // var to store addition information for download

    public void writeLock(OutputStream out) throws IOException {
        String data = name;
        if (version != null) data += "\n\tversion: "+version;
        if (game != null) data += "\n\tgame: "+game;
        if (homepage != null) data += "\n\thomepage: "+homepage;
        if (packageurl != null) data += "\n\tsource: "+packageurl;
        data += "\n\n";
        out.write(data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "\""+name+"\"<"+game+"@"+version+">("+homepage+")";
    }
}
