package com.dosmike.spsauce.utils;

import com.dosmike.spsauce.Executable;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class WebSoup {

    public static final int DEFAULT_WAIT = 800;

    private static final Random random = new Random(System.currentTimeMillis());

    private final String baseUrl;
    private final Map<String,String> cookies = new HashMap<>();

    public WebSoup(String baseUrl) {
        if (baseUrl.endsWith("/")) //no trailing / please
            this.baseUrl = baseUrl.substring(0,baseUrl.length()-1);
        else
            this.baseUrl = baseUrl;
    }

    private String referer = null; // where do we come from
    private HttpURLConnection coreOpenConnection(String relative, @Nullable String contentType) throws IOException {
        //to this url
        String where = baseUrl+'/'+relative;
        HttpURLConnection connection = (HttpURLConnection) new URL(where).openConnection();
        connection.setInstanceFollowRedirects(true);
        //set method accordingly
        connection.setRequestMethod(contentType!=null?"POST":"GET");
        //set a bunch of request properties to hopefully get by cloudflare or whatever ddos protection
        connection.setRequestProperty("User-Agent", Executable.UserAgent);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "de,en-US;q=0.7,en;q=0.3");
        connection.setRequestProperty("Accept-Encoding", "identity"); //i don't want to bother with compression
        if (contentType!=null) {
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
        }
        connection.setRequestProperty("Origin", baseUrl);
        if (referer == null) referer = where;
        connection.setRequestProperty("Referer", referer);
        referer = where;
        if (!cookies.isEmpty()) {
            connection.setRequestProperty("Cookie", toCookieEncode(cookies));
        }
        connection.setDoInput(true);
        return connection;
    }
    private Document coreHandleResponse(String relative, HttpURLConnection connection) throws IOException {
        String where = baseUrl+'/'+relative;
        //send and get response code
        if (connection.getResponseCode() >= 400 || connection.getResponseCode() < 200)
            throw new IOException("Could not query AlliedMods.net: Response "+connection.getResponseCode()+" - "+connection.getResponseMessage());
        //parse headers
        Map<String, List<String>> responseHeaders = connection.getHeaderFields();
        String charset = "UTF-8";
        //content type has information on the content body charset
        List<String> values;
        values = getStringMapCI(responseHeaders, "content-type").orElseGet(LinkedList::new);
        if (!values.isEmpty()) {
            charset = values.get(0);
            int csat = charset.indexOf("charset=");
            int semi = charset.indexOf(';', csat);
            if (csat>=0) {
                if (semi > 0) charset = charset.substring(csat + 8, semi);
                else charset = charset.substring(csat + 8);
            }
        }
        //cookies are required to verify us as user
        values = getStringMapCI(responseHeaders, "set-cookie").orElseGet(LinkedList::new);
        if (!values.isEmpty()) {
            for (String s : values) {
                int sep = s.indexOf(';');
                if (sep > 0) s = s.substring(0,sep);
                sep = s.indexOf('=');
                if (sep < 0) continue;//?
                cookies.put(s.substring(0,sep), s.substring(sep+1));
            }
        }
        //return body
        return Jsoup.parse(connection.getInputStream(),charset,where);
    }

    public Document query(String relative, Map<String,String> postData) throws IOException {
        //to this url
        HttpURLConnection con = coreOpenConnection(relative, postData==null ? null : "application/x-www-form-urlencoded");
        //write post data, sets content length
        if (postData != null) {
            OutputStream out = con.getOutputStream();
            out.write(toUrlEncode(postData).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        return coreHandleResponse(relative, con);
    }

    public static class MultiPartForm extends HashMap<String,List<MultiPartFormValue>> {
        public MultiPartForm() { super(); }

        public List<MultiPartFormValue> add(String key, String value) {
            return add(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> add(String key, Path value) {
            return add(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> add(String key, File value) {
            return add(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> add(String key, MultiPartFormValue value) {
            List<MultiPartFormValue> values = super.computeIfAbsent(key,k->new LinkedList<>());
            values.add(value);
            return values;
        }

        public List<MultiPartFormValue> set(String key, String value) {
            return set(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> set(String key, Path value) {
            return set(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> set(String key, File value) {
            return set(key, new MultiPartFormValue(value));
        }
        //replaces the value with a new singleton list, returns previously associated list
        public List<MultiPartFormValue> set(String key, MultiPartFormValue value) {
            return super.put(key, Collections.singletonList(value));
        }

        public List<MultiPartFormValue> put(String key, String value) {
            return put(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> put(String key, Path value) {
            return put(key, new MultiPartFormValue(value));
        }
        public List<MultiPartFormValue> put(String key, File value) {
            return put(key, new MultiPartFormValue(value));
        }
        //automatically decides if a value should be appended or replaced into the map based on the key
        // form data names ending with [] denote lists
        public List<MultiPartFormValue> put(String key, MultiPartFormValue value) {
            if (key.endsWith("[]"))
                return add(key,value);
            else
                return set(key, value);
        }
        //copy document input element name and value. for compat and simplicity, this will ignore null-arguments
        public List<MultiPartFormValue> put(@Nullable Element element) {
            if (element == null) return null; //Jsoup returns null if an element was not found by e.g. selectFirst
            if (!element.tagName().equalsIgnoreCase("input"))
                throw new IllegalArgumentException("Only input elements are supported");
            String name = element.attr("name");
            String value = element.val();
            assert !name.isEmpty();
            return put(name, value);
        }
    }

    public static class MultiPartFormValue {
        private final Object value;
        private final String oFilename;
        private String oContentType=null; //cache
        public MultiPartFormValue(String value) {
            this.value = value;
            this.oFilename = null;
        }
        public MultiPartFormValue(Path value) {
            this.value = value;
            this.oFilename = value.getFileName().toString();
        }
        public MultiPartFormValue(File value) {
            this(value.toPath());
        }
        private MultiPartFormValue(Object value, String filename, String contentType) {
            this.value = value;
            this.oFilename = filename;
            this.oContentType = contentType;
        }
        void write(OutputStream stream) throws IOException {
            if (value instanceof String) {
                stream.write(((String) value).getBytes(StandardCharsets.UTF_8));
            } else if (value instanceof Path) {
                Files.copy((Path) value, stream);
            }
        }
        String getFilename() {
            return oFilename;
        }
        String getContentType() throws IOException {
            if (!(value instanceof Path)) throw new IllegalStateException("Value is not a file");
            if (oContentType != null) return oContentType;
            String mime = Files.probeContentType((Path) value);
            if (mime == null) {
                byte[] peekBuffer = new byte[2048];
                try (InputStream in = Files.newInputStream((Path) value, StandardOpenOption.READ)) {
                    int read = in.read(peekBuffer);
                    //use octet stream if any of the peeked bytes appear to be non-ascii
                    // since java bytes are signed and ascii never uses the high bit, non-ascii chars appear negative
                    for (int i = 0; i < read; i++) if (peekBuffer[i] <= 0) return oContentType="application/octet-stream";
                    return oContentType="text/plain";
                }
            } else return oContentType=mime;
        }
        public static final MultiPartFormValue EMPTY_FILE = new MultiPartFormValue("","","application/octet-stream");
    }

    public Document multiPartFormData(String relative, Map<String,List<MultiPartFormValue>> postData) throws IOException {
        //the boundary just has to be a long unique string leading a line that is unlikely to appear in any value
        StringBuilder boundary = new StringBuilder(64);
        boundary.append("------------------------");
        while (boundary.length() < 64) boundary.append(random.nextInt(10));
        //to this url
        HttpURLConnection con = coreOpenConnection(relative, postData==null ? null : "multipart/form-data; boundary="+boundary);
        //write post data, sets content length
        if (postData != null) {
            OutputStream out = con.getOutputStream();
            streamMultiPartFormData(out, postData, boundary.toString());
            out.flush();
        }
        return coreHandleResponse(relative, con);
    }

    private String toCookieEncode(Map<String,String> data) {
        return data.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining("; "));
    }
    private String toUrlEncode(Map<String,String> data) throws IOException {
        if (data == null) return "";
        StringBuilder concat= new StringBuilder();
        for (Map.Entry<String, String> e : data.entrySet()) {
            String kv = URLEncoder.encode(e.getKey(), "UTF-8")+"="+URLEncoder.encode(e.getValue(), "UTF-8");
            if (concat.length() > 0) concat.append("&");
            concat.append(kv);
        }
        return concat.toString();
    }

    private void streamMultiPartFormData(OutputStream outputStream, Map<String,List<MultiPartFormValue>> data, String boundary) throws IOException {
        for (Map.Entry<String, List<MultiPartFormValue>> entry : data.entrySet()) {
            //keys / names can repeat for name[] entries
            for (MultiPartFormValue value : entry.getValue()) {
                String fieldHead = "--" + boundary + "\r\n";
                //keys we use are known to be safe, but this should actually %-escape (not url escape)
                fieldHead += "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"";
                if (value.getFilename() != null) {
                    fieldHead += "; filename=\"" + URLEncoder.encode(value.getFilename(), "UTF-8") + "\"";
                    fieldHead += "\r\nContent-Type: " + value.getContentType();
                }
                fieldHead += "\r\n\r\n";
                outputStream.write(fieldHead.getBytes(StandardCharsets.UTF_8));
                value.write(outputStream);
                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    //headers are CI, so we need to look up response headers CI as well
    private <T> Optional<T> getStringMapCI(Map<String,T> map, @NotNull String key) {
        return map
                .keySet()
                .stream()
                .filter(key::equalsIgnoreCase)
                .findAny()
                .map(map::get);
    }

    public void wait(int ms) {
        try {
            Thread.sleep(ms+random.nextInt(300));
        } catch (InterruptedException interruptedException) {
            throw new RuntimeException("AlliedMods interaction was interrupted");
        }
    }

    public Map<String,String> getCookies() {
        return cookies;
    }

}
