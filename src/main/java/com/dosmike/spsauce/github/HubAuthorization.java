package com.dosmike.spsauce.github;

import com.dosmike.spsauce.Authorization;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitChecker;
import org.kohsuke.github.RateLimitTarget;

import java.io.IOException;
import java.util.regex.Pattern;

public class HubAuthorization implements Authorization {

    final GitHub hub;

    //https://de.wikipedia.org/wiki/JSON_Web_Token
    private static final Pattern TOKEN_JWT = Pattern.compile("^[a-zA-Z0-9%_-]+\\.[a-zA-Z0-9%_-]+\\.[a-zA-Z0-9%_-]+$");

    //https://github.blog/changelog/2021-03-31-authentication-token-format-updates-are-generally-available/
    //personal and oauth use .withOAuth
    private static final Pattern TOKEN_OAUTH = Pattern.compile("^gh[po]_[A-Za-z0-9_]+$");
    private static final Pattern TOKEN_APPTYPE = Pattern.compile("^gh[usr]_[A-Za-z0-9_]+$");
    private static final Pattern TOKEN_LEGACY = Pattern.compile("^[a-f0-9]+$");

    public HubAuthorization(String token, String login) throws IOException {
        GitHubBuilder ghb = new GitHubBuilder();

        if (TOKEN_JWT.matcher(token).matches()) {
            ghb.withJwtToken(token);
        } else if (TOKEN_OAUTH.matcher(token).matches()) {
            if (login != null) ghb.withOAuthToken(token, login);
            else ghb.withOAuthToken(token);
        } else if (TOKEN_APPTYPE.matcher(token).matches()) {
            ghb.withAppInstallationToken(token);
        } else if (TOKEN_LEGACY.matcher(token).matches()) {
            //assume pat for sps compat with previous behaviour
            if (login != null) ghb.withOAuthToken(token, login);
            else ghb.withOAuthToken(token);
        }
        ghb.withRateLimitChecker(new RateLimitChecker.LiteralValue(60), RateLimitTarget.CORE);
        hub = ghb.build();
        if (!hub.isCredentialValid() || hub.isAnonymous())
            throw new RuntimeException("Invalid or missing auth credentials for GitHub");
    }

}
