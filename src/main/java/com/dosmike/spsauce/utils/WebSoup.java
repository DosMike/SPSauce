package com.dosmike.spsauce.utils;

import com.dosmike.spsauce.Executable;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static class MultiPartForm extends HashMap<String,List<MultiPartFormValue>> {
        public MultiPartForm() { super(); }
        public MultiPartForm(Element formElement) {
            if (formElement != null) {
                if (!"form".equalsIgnoreCase(formElement.tagName()))
                    throw new IllegalArgumentException("MultiPartForm constructor requires <form>");
                put(formElement);
            }
        }

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
        // allowed element tags: input, textarea, select, form (will not read input[type=submit])
        public void put(@Nullable Element element) {
            if (element == null) return;
            String name = element.attr("name");
            String value;
            if (element.tagName().equalsIgnoreCase("input") || element.tagName().equalsIgnoreCase("textarea")) {
                value = element.val();
                assert !name.isEmpty();
            } else if (element.tagName().equalsIgnoreCase("select")) {
                Element selected = element.selectFirst("option[selected]");
                value = selected == null ? "" : selected.attr("value");
            } else if (element.tagName().equalsIgnoreCase("form")) {
                element.select("input:not([type=submit]),textarea,select").forEach(this::put);
                return;
            } else throw new IllegalArgumentException("Only input elements are supported");
            put(name, value);
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
            if (!(value instanceof Path) && (oFilename == null))
                throw new IllegalStateException("Value is not a file");
            if (oContentType == null) oContentType = BaseIO.getMimeType((Path) value);
            return oContentType;
        }
        public static final MultiPartFormValue EMPTY_FILE = new MultiPartFormValue("","","application/octet-stream");

        @Override
        public String toString() {
            return value.toString();
        }
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
    /** use a multipart form as that's more convenient, and convert it to url form data. THIS STILL USES POST.
     * Note: If you got the awful idea to send actual data via GET request, use toUrlEncode and concat to the url. */
    public Document queryForm(String relative, Map<String,List<MultiPartFormValue>> postData) throws IOException {
        //to this url
        HttpURLConnection con = coreOpenConnection(relative, postData==null ? null : "application/x-www-form-urlencoded");
        //write post data, sets content length
        if (postData != null) {
            OutputStream out = con.getOutputStream();
            out.write(toUrlEncodeForm(postData).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        return coreHandleResponse(relative, con);
    }

    public Document submitForm(String relative, Map<String,List<MultiPartFormValue>> postData) throws IOException {
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
    private String toUrlEncodeForm(Map<String,List<MultiPartFormValue>> data) throws IOException {
        if (data == null) return "";
        StringBuilder concat= new StringBuilder();
        for (Map.Entry<String, List<MultiPartFormValue>> e : data.entrySet()) {
            for (MultiPartFormValue value : e.getValue()) {
                String kv = URLEncoder.encode(e.getKey(), "UTF-8") + "=" + URLEncoder.encode(value.toString(), "UTF-8");
                if (concat.length() > 0) concat.append("&");
                concat.append(kv);
            }
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
