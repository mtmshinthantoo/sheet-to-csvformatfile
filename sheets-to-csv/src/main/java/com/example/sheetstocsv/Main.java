package com.example.sheetstocsv;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public class Main {

    // ------------------ Utility Methods ------------------
    private static String generateCsvFileName(String sheetName) {
        String safeSheetName = sheetName.replaceAll("[^a-zA-Z0-9_-]", "_");
        DateTimeFormatter formatter
                = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        return safeSheetName + "_" + timestamp + ".csv";
    }

    private static String buildOAuthUrl(String clientId, String redirectUri) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=https://www.googleapis.com/auth/spreadsheets"
                + "&access_type=offline"
                + "&prompt=consent";
    }

    // ------------------ Main Entry ------------------
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n========= MAIN MENU =========");
            System.out.println("1. Export Google Sheets -> CSV");
            System.out.println("2. Import CSV -> Google Sheets");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    runExportFlow(scanner);
                    break;
                case "2":
                    runImportMenu(scanner);
                    break;
                case "0":
                    System.out.println("Exiting application. Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    // ------------------ IMPORT SUB-MENU ONLY ------------------
    private static void runImportMenu(Scanner scanner) {

        while (true) {
            System.out.println("\n----- Import CSV -> Google Sheets -----");
            System.out.println("1. Update (overwrite existing data)");
            System.out.println("2. Append (add new rows)");
            System.out.println("0. Back");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1":
                    System.out.println("Update selected (not implemented yet).");
                    break;

                case "2":
                    System.out.println("\n--- Import CSV -> Append ---");

                    try {
                        // Load config
                        ConfigLoader config = new ConfigLoader();

                        String clientId = config.get("google.client.id");
                        String clientSecret = config.get("google.client.secret");
                        String sheetId = config.get("google.sheet.id");

                        // Ask CSV path
                        System.out.print("Enter CSV file path: ");
                        Path csvPath = Paths.get(scanner.nextLine().trim());

                        if (!Files.exists(csvPath) || !Files.isRegularFile(csvPath)) {
                            System.out.println("Invalid CSV file path.");
                            break;
                        }

                        // Get refresh token
                        if (!config.hasValue("google.refresh.token")) {
                            throw new RuntimeException(
                                    "No refresh token found. Please run export once."
                            );
                        }

                        String refreshToken = config.get("google.refresh.token");

                        // Refresh access token
                        RefreshTokenService refreshService
                                = new RefreshTokenService(clientId, clientSecret);

                        String accessToken
                                = refreshService.refreshAccessToken(refreshToken);

                        // Create sheet service to list sheets
                        GoogleSheetService sheetService
                                = new GoogleSheetService(sheetId, "");

                        List<String> availableSheets
                                = sheetService.listSheetNames(accessToken);

                        if (availableSheets.isEmpty()) {
                            System.out.println("No sheets found in spreadsheet.");
                            break;
                        }

                        // Show sheets (same UX as Export)
                        System.out.println("\nAvailable sheets:");
                        for (int i = 0; i < availableSheets.size(); i++) {
                            System.out.println((i + 1) + ". " + availableSheets.get(i));
                        }

                        // User selects target sheet
                        int selection;
                        while (true) {
                            System.out.print("Select sheet number to append to: ");
                            if (scanner.hasNextInt()) {
                                selection = scanner.nextInt();
                                scanner.nextLine(); // consume newline
                                if (selection >= 1 && selection <= availableSheets.size()) {
                                    break;
                                }
                            } else {
                                scanner.nextLine();
                            }
                            System.out.println("Invalid selection. Try again.");
                        }

                        String targetSheetName
                                = availableSheets.get(selection - 1);

                        // Create services
                        CsvReaderService csvReaderService
                                = new CsvReaderService();

                        ImportService importService
                                = new ImportService(
                                        csvReaderService,
                                        sheetService
                                );

                        // Execute import -> append
                        importService.importAppend(
                                csvPath,
                                accessToken,
                                targetSheetName
                        );

                    } catch (Exception e) {
                        System.err.println("Import failed:");
                        e.printStackTrace(System.err);
                    }
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    // ------------------ EXPORT FLOW (UNCHANGED) ------------------
    private static void runExportFlow(Scanner scanner) {

        System.out.println("\n--- Export Google Sheets -> CSV ---");

        try {
            ConfigLoader config = new ConfigLoader();
            String clientId = config.get("google.client.id");
            String clientSecret = config.get("google.client.secret");
            String redirectUri = config.get("redirect.uri");
            String sheetId = config.get("google.sheet.id");
            String configuredSheetName = config.get("google.sheet.name");
            String sheetRange = config.get("google.sheet.range");

            String refreshToken;

            if (!config.hasValue("google.refresh.token")) {
                System.out.println("No refresh token found. Running OAuth flow...");

                LocalOAuthCallbackServer callbackServer
                        = new LocalOAuthCallbackServer(8080);
                callbackServer.start();

                String oauthUrl = buildOAuthUrl(clientId, redirectUri);
                System.out.println("Opening browser for Google authorization...");

                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(oauthUrl));
                } else {
                    System.out.println("Please open the following URL manually:");
                    System.out.println(oauthUrl);
                }

                String authorizationCode
                        = callbackServer.waitForAuthorizationCode();
                callbackServer.stop();

                if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
                    throw new RuntimeException("Failed to obtain authorization code.");
                }

                OAuthService oauthService
                        = new OAuthService(clientId, clientSecret, redirectUri);

                TokenResponse tokens
                        = oauthService.runAuthorization(authorizationCode);

                refreshToken = tokens.getRefreshToken();
                config.setAndSave("google.refresh.token", refreshToken);

                System.out.println("Authorization successful.");
            } else {
                refreshToken = config.get("google.refresh.token");
            }

            RefreshTokenService refreshService
                    = new RefreshTokenService(clientId, clientSecret);

            String accessToken
                    = refreshService.refreshAccessToken(refreshToken);

            System.out.println("Access token obtained.");

            GoogleSheetService sheetService
                    = new GoogleSheetService(sheetId, "");

            String chosenSheetName;

            if (configuredSheetName != null && !configuredSheetName.trim().isEmpty()) {
                chosenSheetName = configuredSheetName;
                System.out.println("Using sheet from config: " + chosenSheetName);
            } else {
                List<String> availableSheets
                        = sheetService.listSheetNames(accessToken);

                if (availableSheets.isEmpty()) {
                    throw new RuntimeException("No sheets found in spreadsheet.");
                }

                System.out.println("\nAvailable sheets:");
                for (int i = 0; i < availableSheets.size(); i++) {
                    System.out.println((i + 1) + ". " + availableSheets.get(i));
                }

                int selection;
                while (true) {
                    System.out.print("Select sheet number to export: ");
                    if (scanner.hasNextInt()) {
                        selection = scanner.nextInt();
                        scanner.nextLine();
                        if (selection >= 1 && selection <= availableSheets.size()) {
                            break;
                        }
                    } else {
                        scanner.nextLine();
                    }
                    System.out.println("Invalid selection. Try again.");
                }

                chosenSheetName = availableSheets.get(selection - 1);
            }

            String effectiveRange;
            if (sheetRange == null || sheetRange.trim().isEmpty()) {
                effectiveRange = chosenSheetName;
            } else if (sheetRange.contains("!")) {
                effectiveRange = sheetRange;
            } else {
                effectiveRange = chosenSheetName + "!" + sheetRange;
            }

            GoogleSheetService dataService
                    = new GoogleSheetService(sheetId, effectiveRange);

            String sheetJson
                    = dataService.fetchSheetData(accessToken);

            CsvWriterService csvWriter = new CsvWriterService();
            String outputFileName = generateCsvFileName(chosenSheetName);

            Path exportDir = Paths.get("Exported_CSV");
            Files.createDirectories(exportDir);

            Path outputPath = exportDir.resolve(outputFileName);
            csvWriter.writeCsv(sheetJson, outputPath);

            System.out.println("CSV exported to: " + outputPath);

        } catch (Exception e) {
            System.err.println("Export failed:");
            e.printStackTrace(System.err);
        }
    }
}
