package com.dosmike.spsauce.am;

import com.dosmike.spsauce.Authorization;
import com.dosmike.spsauce.utils.WebSoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class AMAuthorization implements Authorization, AutoCloseable {

    private final String logoutToken;
    private final String self;

    private WebSoup web;

    public AMAuthorization(String username, String password) throws IOException {
        this.self = username;
        this.web = new WebSoup("https://forums.alliedmods.net/");

        //create session on remote, get s from login
        Elements s = web.query("login.php", null).select("form input[name=s]");
        String t = s.attr("value");
        if (s.isEmpty() || t.isEmpty())
            throw new RuntimeException("Could not query AlliedMods login page, probably blocked by DDoS protection! "+t+"+"+s.size());
        web.wait(WebSoup.DEFAULT_WAIT); //relax
        //send login information
        Map<String,String> loginForm = new HashMap<>();
        loginForm.put("vb_login_username", username);
        loginForm.put("vb_login_password", password);
        loginForm.put("s", t);
        loginForm.put("securitytoken","guest");
        loginForm.put("do", "login");
        loginForm.put("vb_login_md5password", md5(transform(password.trim())));
        loginForm.put("vb_login_md5password_utf", md5(password.trim()));
        Document loginResponse = web.query("login.php", loginForm);
        if (!loginResponse.select("form blockquote strong").text().trim().startsWith("Thank you for logging in,")) {
            throw new RuntimeException("Authentication failed!");
        }
        web.wait(2000); //auto redirect is 2s
        Document doc = web.query("index.php", null);
        Element e = doc.selectFirst("a[href^=\"login.php?do=logout\"]");
        if (e==null) {
            throw new RuntimeException("Login denied");
        }
        logoutToken = e.attr("href");
        web.wait(WebSoup.DEFAULT_WAIT); //relax
    }

    public boolean isSelf(String username) {
        return self.equalsIgnoreCase(username);
    }

    @Override
    public void close() throws Exception {
        if (web.getCookies().isEmpty() || logoutToken==null) return;
        System.out.println("Logging out from AlliedMods");
        web.query(logoutToken, null);
        web.getCookies().clear();
    }

    private String transform(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEachOrdered(cp->{if (cp>255) sb.append("&#").append(cp).append(";"); else sb.append((char)cp);});
        return sb.toString();
    }
    private String md5(String data) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {throw new RuntimeException("Missing mandatory algorithm: MD5", e);}
        byte[] hashBytes = md5.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte hashByte : hashBytes) sb.append(String.format("%02X", hashByte));
        return sb.toString();
    }

    public WebSoup getBrowser() {
        return web;
    }
}
