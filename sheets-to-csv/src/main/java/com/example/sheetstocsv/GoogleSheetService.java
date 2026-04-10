package com.example.sheetstocsv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GoogleSheetService {

    private final String sheetId;
    private final String range;

    public GoogleSheetService(String sheetId, String range) {
        this.sheetId = sheetId;
        this.range = range;
    }

    public String fetchSheetData(String accessToken) throws Exception {

        String encodedRange = URLEncoder.encode(range, StandardCharsets.UTF_8);
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

    // Method to show all sheet names in the spreadsheet
    public List<String> listSheetNames(String accessToken) throws Exception {
        String url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + sheetId
                + "?fields=sheets.properties.title";

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to list sheet names. HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }

        // Parse the response to extract sheet names
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode sheetsNode = root.get("sheets");

        List<String> sheetNames = new ArrayList<>();

        if (sheetsNode != null && sheetsNode.isArray()) {
            for (JsonNode sheet : sheetsNode) {
                JsonNode titleNode = sheet.path("properties").path("title");
                if (!titleNode.isMissingNode()) {
                    sheetNames.add(titleNode.asText());
                }
            }
        }
        return sheetNames;
    }
}
