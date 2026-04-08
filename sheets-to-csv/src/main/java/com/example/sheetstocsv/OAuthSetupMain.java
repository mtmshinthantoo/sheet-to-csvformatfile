package com.example.sheetstocsv;

public class OAuthSetupMain {

    private static final String CLIENT_ID = "${google.client.id}";
    private static final String CLIENT_SECRET = "${google.client.secret}";
    private static final String REDIRECT_URI = "${redirect.uri}";

    public static void main(String[] args) throws Exception {

        String authorizationCode = "${authorization.code}";

        OAuthTokenService service
                = new OAuthTokenService(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);

        TokenResponse tokens
                = service.exchangeAuthorizationCode(authorizationCode);

        System.out.println("ACCESS TOKEN:");
        System.out.println(tokens.getAccessToken());

        System.out.println("REFRESH TOKEN (SAVE THIS!):");
        System.out.println(tokens.getRefreshToken());
    }
}
