package com.example.sheetstocsv;

public class OAuthService {

    private final OAuthTokenService tokenService;

    public OAuthService(String clientId, String clientSecret, String redirectUri) {
        this.tokenService = new OAuthTokenService(clientId, clientSecret, redirectUri);
    }

    public TokenResponse runAuthorization(String authorizationCode) throws Exception {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        return tokenService.exchangeAuthorizationCode(authorizationCode.trim());
    }
}
