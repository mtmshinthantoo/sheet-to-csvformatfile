package com.example.sheetstocsv;

import java.nio.file.Paths;

public class Main {

    private static final String CLIENT_ID = "${google.client.id}";
    private static final String CLIENT_SECRET = "${google.client.secret}";
    private static final String REFRESH_TOKEN = "${google.refresh.token}";

    public static void main(String[] args) {
        System.out.println("Starting Sheets to CSV converter...");
        try {
            // Refresh access token
            RefreshTokenService refreshTokenService
                    = new RefreshTokenService(CLIENT_ID, CLIENT_SECRET);

            String accessToken
                    = refreshTokenService.refreshAccessToken(REFRESH_TOKEN);

            System.out.println("Access token obtained successfully.");

            // Call Google Sheets API
            GoogleSheetService sheetService = new GoogleSheetService();
            String sheetJson = sheetService.fetchSheetData(accessToken);

            System.out.println("Sheet data received successfully.");
            System.out.println(sheetJson);
            // Write CSV file
            CsvWriterService csvWriterService = new CsvWriterService();
            csvWriterService.writeCsv(sheetJson, Paths.get("Employees.csv"));

            System.out.println("CSV file created successfully.");
            System.out.println("Process completed.");

        } catch (Exception e) {
            System.err.println("Application failed:");
        }
    }
}
