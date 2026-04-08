package com.example.sheetstocsv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GoogleSheetService {

    private static final String SHEET_ID = "${google.sheet.id}";
    private static final String RANGE = "Employees!A1:F";

    public String fetchSheetData(String accessToken) throws Exception {
        String encodeRange = URLEncoder.encode(RANGE, StandardCharsets.UTF_8);
        String url = "https://sheets.googleapis.com/v4/spreadsheets/" + SHEET_ID + "/values/" + encodeRange;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch sheet data. HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }
}
