package com.example.sheetstocsv;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {

    private static String generateCsvFileName(String sheetName) {
        String newSheetName = sheetName.replaceAll("[^a-zA-Z0-9_-]", "_");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        return newSheetName + "_" + timestamp + ".csv";
    }

    public static void main(String[] args) {
        System.out.println("Starting Sheets to CSV converter...");

        try {
            // Load configuration
            ConfigLoader config = new ConfigLoader();
            String clientId = config.get("google.client.id");
            String clientSecret = config.get("google.client.secret");
            String redirectUri = config.get("redirect.uri");
            String sheetId = config.get("google.sheet.id");
            String sheetName = config.get("google.sheet.name");
            String sheetRange = config.get("google.sheet.range");

            String refreshToken;

            //First run detection 
            if (!config.hasValue("google.refresh.token")) {
                System.out.println("No refresh token found. Running OAuth flow...");

                String authorizationCode = config.get("authorization.code");
                OAuthService oauthService = new OAuthService(clientId, clientSecret, redirectUri);

                TokenResponse tokens = oauthService.runAuthorization(authorizationCode);

                refreshToken = tokens.getRefreshToken();
                config.setAndSave("google.refresh.token", refreshToken);
                System.out.println("Authorization successful. Refresh token saved successfully.");
            } else {
                //if refresh token already exists
                refreshToken = config.get("google.refresh.token");
            }

            //Refresh access token using refresh token
            RefreshTokenService refreshService = new RefreshTokenService(clientId, clientSecret);
            String accessToken = refreshService.refreshAccessToken(refreshToken);
            System.out.println("Access token obtained successfully.");

            // Determine effective range
            String chosenRange;
            if (sheetRange == null || sheetRange.isBlank()) {
                chosenRange = sheetName;
            } else if (sheetRange.contains("!")) {
                chosenRange = sheetRange;
            } else {
                chosenRange = sheetName + "!" + sheetRange;
            }

            //Fetch sheet data
            GoogleSheetService sheetService = new GoogleSheetService(sheetId, chosenRange);
            String sheetJson = sheetService.fetchSheetData(accessToken);

            //Convert to CSV
            CsvWriterService csvWriter = new CsvWriterService();
            String outputFileName = generateCsvFileName(sheetName);
            csvWriter.writeCsv(sheetJson, Paths.get(outputFileName));

            System.out.println("Sheet data successfully written to " + outputFileName);
            System.out.println("Application completed successfully.");

        } catch (Exception e) {
            System.err.println("Application failed:");
            e.printStackTrace(System.err);
        }
    }
}
