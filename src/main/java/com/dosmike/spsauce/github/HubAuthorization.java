package com.dosmike.spsauce.github;

import com.dosmike.spsauce.Authorization;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitChecker;
import org.kohsuke.github.RateLimitTarget;

import java.io.IOException;

public class HubAuthorization implements Authorization {

    final GitHub hub;

    public HubAuthorization(String pat, String login) throws IOException {
        GitHubBuilder ghb = new GitHubBuilder();
        if (login != null) ghb.withOAuthToken(pat, login);
        else ghb.withOAuthToken(pat);
        ghb.withRateLimitChecker(new RateLimitChecker.LiteralValue(60), RateLimitTarget.CORE);
        hub = ghb.build();
        if (!hub.isCredentialValid() || hub.isAnonymous())
            throw new RuntimeException("Invalid or missing auth credentials for GitHub");
    }

}
