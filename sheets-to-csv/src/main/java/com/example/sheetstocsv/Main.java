package com.example.sheetstocsv;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static String generateCsvFileName(String sheetName) {
        String newSheetName = sheetName.replaceAll("[^a-zA-Z0-9_-]", "_");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
        String timestamp = LocalDateTime.now().format(formatter);
        return newSheetName + "_" + timestamp + ".csv";
    }

    private static String buildOAuthUrl(String clientId, String redirectUri) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=https://www.googleapis.com/auth/spreadsheets.readonly"
                + "&access_type=offline"
                + "&prompt=consent";
    }
    // https://accounts.google.com/o/oauth2/v2/auth?client_id=62338090647-63aujn6nca8aqd25oegji5qkl0rgesar.apps.googleusercontent.com&redirect_uri=http://localhost:8080/callback&response_type=code&scope=https://www.googleapis.com/auth/spreadsheets.readonly&access_type=offline&prompt=consent

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

                // Start local callback server
                LocalOAuthCallbackServer callbackServer = new LocalOAuthCallbackServer(8080);
                callbackServer.start();

                // Open browser for user authorization
                String oautUrl = buildOAuthUrl(clientId, redirectUri);
                System.out.println("Opening brower for Google authorization......");

                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(oautUrl));
                } else {
                    System.out.println("Please open the following URL in your browser to authorize the application:");
                    System.out.println(oautUrl);
                }
                // Wait for authorization code
                String authorizationCode = callbackServer.waitForAuthorizationCode();
                // Stop the callback server
                callbackServer.stop();

                if (authorizationCode == null || authorizationCode.isBlank()) {
                    throw new RuntimeException("Failed to obtain authorization code.");
                }
                // Exchange authorization code for tokens
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

            // Select sheet name
            GoogleSheetService sheetService = new GoogleSheetService(sheetId, "");
            String chosenSheetName;

            if (sheetName != null && !sheetName.isBlank()) {
                chosenSheetName = sheetName;
                System.out.println("Using sheet name from configuration: " + chosenSheetName);
            } else {
                List<String> availableSheets = sheetService.listSheetNames(accessToken);

                if (availableSheets.isEmpty()) {
                    throw new RuntimeException("No sheets found in the spreadsheet.");
                }

                System.out.println("Available sheets: ");
                for (int i = 0; i < availableSheets.size(); i++) {
                    System.out.println((i + 1) + ". " + availableSheets.get(i));
                }

                Scanner scanner = new Scanner(System.in);
                int selection;

                while (true) {
                    System.out.println("Please select sheet number to export: ");
                    if (scanner.hasNextInt()) {
                        selection = scanner.nextInt();
                        if (selection >= 1 && selection <= availableSheets.size()) {
                            break;
                        }
                    } else {
                        scanner.next(); // Consume invalid input
                    }
                    System.out.println("Invalid selection. Please enter a number between 1 and " + availableSheets.size());
                }
                chosenSheetName = availableSheets.get(selection - 1);
                System.out.println("Selected sheet: " + chosenSheetName);
            }

            // Determine effective range
            String chosenRange;
            if (sheetRange == null || sheetRange.isBlank()) {
                chosenRange = chosenSheetName;
            } else if (sheetRange.contains("!")) {
                chosenRange = sheetRange;
            } else {
                chosenRange = chosenSheetName + "!" + sheetRange;
            }

            //Fetch sheet data
            GoogleSheetService dataService = new GoogleSheetService(sheetId, chosenRange);
            String sheetJson = dataService.fetchSheetData(accessToken);

            //Convert to CSV
            CsvWriterService csvWriter = new CsvWriterService();
            String outputFileName = generateCsvFileName(chosenSheetName);
            csvWriter.writeCsv(sheetJson, Paths.get(outputFileName));

            System.out.println("Sheet data successfully written to " + outputFileName);
            System.out.println("Application completed successfully.");

        } catch (Exception e) {
            System.err.println("Application failed:");
            e.printStackTrace(System.err);
        }
    }
}
