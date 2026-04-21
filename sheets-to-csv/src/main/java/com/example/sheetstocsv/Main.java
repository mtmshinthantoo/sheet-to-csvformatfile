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

    /**
     * Ensures OAuth is completed and returns a valid access token.
     */
    private static String ensureAccessToken() throws Exception {

        ConfigLoader config = new ConfigLoader();

        String clientId = config.get("google.client.id");
        String clientSecret = config.get("google.client.secret");
        String redirectUri = config.get("redirect.uri");

        String refreshToken;

        if (!config.hasValue("google.refresh.token")) {

            LocalOAuthCallbackServer callbackServer
                    = new LocalOAuthCallbackServer(8080);
            callbackServer.start();

            String authUrl = buildOAuthUrl(clientId, redirectUri);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(authUrl));
            } else {
                System.out.println("Open this URL manually:");
                System.out.println(authUrl);
            }

            String authorizationCode
                    = callbackServer.waitForAuthorizationCode();
            callbackServer.stop();

            OAuthService oauthService
                    = new OAuthService(clientId, clientSecret, redirectUri);

            TokenResponse tokens
                    = oauthService.runAuthorization(authorizationCode);

            refreshToken = tokens.getRefreshToken();
            config.setAndSave("google.refresh.token", refreshToken);
        } else {
            refreshToken = config.get("google.refresh.token");
        }

        RefreshTokenService refreshService
                = new RefreshTokenService(clientId, clientSecret);

        return refreshService.refreshAccessToken(refreshToken);
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
                    System.out.println("Invalid option.");
            }
        }
    }

    // ------------------ IMPORT MENU ------------------
    private static void runImportMenu(Scanner scanner) {

        while (true) {
            System.out.println("\n----- Import CSV -> Google Sheets -----");
            System.out.println("1. Update (patch by primary key)");
            System.out.println("2. Append (add new rows)");
            System.out.println("0. Back");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1":
                case "2":
                    try {
                        ConfigLoader config = new ConfigLoader();
                        String sheetId = config.get("google.sheet.id");
                        String accessToken = ensureAccessToken();

                        System.out.print("Enter CSV file path: ");
                        Path csvPath = Paths.get(scanner.nextLine().trim());

                        if (!Files.isRegularFile(csvPath)) {
                            System.out.println("Invalid CSV file path.");
                            break;
                        }

                        GoogleSheetService sheetService
                                = new GoogleSheetService(sheetId, "");

                        List<String> sheets
                                = sheetService.listSheetNames(accessToken);

                        if (sheets.isEmpty()) {
                            System.out.println("No sheets found.");
                            break;
                        }

                        System.out.println("\nAvailable sheets:");
                        for (int i = 0; i < sheets.size(); i++) {
                            System.out.println((i + 1) + ". " + sheets.get(i));
                        }

                        int selection;
                        while (true) {
                            System.out.print("Select sheet number: ");
                            if (scanner.hasNextInt()) {
                                selection = scanner.nextInt();
                                scanner.nextLine();
                                if (selection >= 1 && selection <= sheets.size()) {
                                    break;
                                }
                            } else {
                                scanner.nextLine();
                            }
                            System.out.println("Invalid selection.");
                        }

                        String targetSheet = sheets.get(selection - 1);

                        ImportService importService
                                = new ImportService(
                                        new CsvReaderService(),
                                        sheetService
                                );

                        if (choice.equals("1")) {
                            importService.importUpdate(
                                    csvPath,
                                    accessToken,
                                    targetSheet
                            );
                        } else {
                            importService.importAppend(
                                    csvPath,
                                    accessToken,
                                    targetSheet
                            );
                        }

                    } catch (Exception e) {
                        System.err.println("Import failed:");
                        e.printStackTrace(System.err);
                    }
                    break;

                case "0":
                    return;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ------------------ EXPORT FLOW (UNCHANGED) ------------------
    private static void runExportFlow(Scanner scanner) {

        System.out.println("\n--- Export Google Sheets -> CSV ---");

        try {
            ConfigLoader config = new ConfigLoader();
            String sheetId = config.get("google.sheet.id");
            String sheetRange = config.get("google.sheet.range");

            String accessToken = ensureAccessToken();

            GoogleSheetService service
                    = new GoogleSheetService(sheetId, "");

            List<String> sheets
                    = service.listSheetNames(accessToken);

            if (sheets.isEmpty()) {
                throw new RuntimeException("No sheets found.");
            }

            System.out.println("\nAvailable sheets:");
            for (int i = 0; i < sheets.size(); i++) {
                System.out.println((i + 1) + ". " + sheets.get(i));
            }

            int selection;
            while (true) {
                System.out.print("Select sheet number to export: ");
                if (scanner.hasNextInt()) {
                    selection = scanner.nextInt();
                    scanner.nextLine();
                    if (selection >= 1 && selection <= sheets.size()) {
                        break;
                    }
                } else {
                    scanner.nextLine();
                }
                System.out.println("Invalid selection.");
            }

            String chosenSheet = sheets.get(selection - 1);

            String effectiveRange
                    = (sheetRange == null || sheetRange.isBlank())
                    ? chosenSheet
                    : chosenSheet + "!" + sheetRange;

            GoogleSheetService dataService
                    = new GoogleSheetService(sheetId, effectiveRange);

            String json
                    = dataService.fetchSheetData(accessToken);

            CsvWriterService writer = new CsvWriterService();

            Path exportDir = Paths.get("Exported_CSV");
            Files.createDirectories(exportDir);

            Path output
                    = exportDir.resolve(generateCsvFileName(chosenSheet));

            writer.writeCsv(json, output);

            System.out.println("CSV exported to: " + output);

        } catch (Exception e) {
            System.err.println("Export failed:");
            e.printStackTrace(System.err);
        }
    }
}
