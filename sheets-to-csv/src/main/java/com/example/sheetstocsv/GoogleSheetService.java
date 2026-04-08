package com.example.sheetstocsv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GoogleSheetService {

    private final String sheetId;
    private static final String RANGE = "Title!A1:G";

    public GoogleSheetService(String sheetId) {
        this.sheetId = sheetId;
    }

    public String fetchSheetData(String accessToken) throws Exception {

        String encodedRange = URLEncoder.encode(RANGE, StandardCharsets.UTF_8);
        String url
                = "https://sheets.googleapis.com/v4/spreadsheets/"
                + sheetId
                + "/values/"
                + encodedRange;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response
                = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to fetch sheet data. HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }

        return response.body();
    }
}
