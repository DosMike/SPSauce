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

    // The Regexes here are not for validation of security, only to decide the token type,
    // so we can pass any type properly to the API.

    //https://de.wikipedia.org/wiki/JSON_Web_Token
    private static final Pattern TOKEN_JWT = Pattern.compile("^[\\w%-]+\\.[\\w%-]+\\.[\\w%-]+$");

    //https://github.blog/changelog/2021-03-31-authentication-token-format-updates-are-generally-available/
    //https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/about-authentication-to-github
    //https://gist.github.com/magnetikonline/073afe7909ffdd6f10ef06a00bc3bc88
    //personal and oauth use .withOAuth
    private static final Pattern TOKEN_OAUTH = Pattern.compile("^(gh[po]|github_pat)_\\w+$");
    private static final Pattern TOKEN_APPTYPE = Pattern.compile("^gh[usr]_\\w+|v[0-9]+\\.\\w+$");
    private static final Pattern TOKEN_LEGACY = Pattern.compile("^\\w+$");

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
