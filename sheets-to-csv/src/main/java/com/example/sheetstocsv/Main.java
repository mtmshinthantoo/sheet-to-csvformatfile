package com.example.sheetstocsv;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {
        System.out.println("Starting Sheets to CSV converter...");

        try {
            // Load application.properties
            Properties props = new Properties();
            try (InputStream input = Main.class
                    .getClassLoader()
                    .getResourceAsStream("application.properties")) {

                if (input == null) {
                    throw new RuntimeException("application.properties not found");
                }
                props.load(input);
            }

            // Read configuration values
            String clientId = props.getProperty("google.client.id");
            String clientSecret = props.getProperty("google.client.secret");
            String refreshToken = props.getProperty("google.refresh.token");
            String sheetId = props.getProperty("google.sheet.id");

            // 1. Refresh access token
            RefreshTokenService refreshTokenService
                    = new RefreshTokenService(clientId, clientSecret);

            String accessToken
                    = refreshTokenService.refreshAccessToken(refreshToken);

            System.out.println("Access token obtained successfully.");

            // 2. Fetch Google Sheets data
            GoogleSheetService sheetService
                    = new GoogleSheetService(sheetId);

            String sheetJson
                    = sheetService.fetchSheetData(accessToken);

            // 3. Write CSV
            CsvWriterService csvWriterService = new CsvWriterService();
            csvWriterService.writeCsv(sheetJson, Paths.get("Titles.csv"));

            System.out.println("CSV file created successfully.");
            System.out.println("Process completed.");

        } catch (Exception e) {
            System.err.println("Application failed:");
            e.printStackTrace();
        }
    }
}
