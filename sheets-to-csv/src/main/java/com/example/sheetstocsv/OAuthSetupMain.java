package com.example.sheetstocsv;

import java.io.InputStream;
import java.util.Properties;

public class OAuthSetupMain {

    public static void main(String[] args) throws Exception {

        // Load application.properties
        Properties props = new Properties();
        try (InputStream input
                = OAuthSetupMain.class
                        .getClassLoader()
                        .getResourceAsStream("application.properties")) {

                    if (input == null) {
                        throw new RuntimeException("application.properties not found");
                    }
                    props.load(input);
                }

                // Read properties
                String clientId = require(props, "google.client.id");
                String clientSecret = require(props, "google.client.secret");
                String redirectUri = require(props, "redirect.uri");
                String authorizationCode = require(props, "authorization.code");

                // Exchange authorization code
                OAuthTokenService service
                        = new OAuthTokenService(clientId, clientSecret, redirectUri);

                TokenResponse tokens
                        = service.exchangeAuthorizationCode(authorizationCode.trim());

                // Output tokens (refresh token must be saved)
                System.out.println("ACCESS TOKEN:");
                System.out.println(tokens.getAccessToken());

                System.out.println();
                System.out.println("REFRESH TOKEN (SAVE THIS!):");
                System.out.println(tokens.getRefreshToken());

                System.out.println();
                System.out.println("OAuth setup completed.");
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new RuntimeException("Missing required property: " + key);
        }
        return value;
    }
}
