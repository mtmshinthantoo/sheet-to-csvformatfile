package com.example.sheetstocsv;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<String> listSheetNames(String accessToken) throws Exception {

        String url
                = "https://sheets.googleapis.com/v4/spreadsheets/"
                + sheetId
                + "?fields=sheets.properties.title";

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
                    "Failed to list sheet names. HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode sheetsNode = root.get("sheets");

        List<String> sheetNames = new ArrayList<>();

        if (sheetsNode != null && sheetsNode.isArray()) {
            for (JsonNode sheet : sheetsNode) {
                JsonNode titleNode
                        = sheet.path("properties").path("title");
                if (!titleNode.isMissingNode()) {
                    sheetNames.add(titleNode.asText());
                }
            }
        }

        return sheetNames;
    }

    public void appendValues(
            String accessToken,
            String targetSheetName,
            List<List<String>> values
    ) throws Exception {

        String encodedRange
                = URLEncoder.encode(targetSheetName + "!A1",
                        StandardCharsets.UTF_8);

        String url
                = "https://sheets.googleapis.com/v4/spreadsheets/"
                + sheetId
                + "/values/"
                + encodedRange
                + ":append?valueInputOption=RAW";

        Map<String, Object> body = new HashMap<>();
        body.put("values", values);

        ObjectMapper mapper = new ObjectMapper();
        String requestBody = mapper.writeValueAsString(body);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response
                = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to append values. HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }
    }

    /**
     * Fetches all values (headers + data rows) from a sheet.
     */
    public List<List<String>> fetchAllValues(
            String accessToken,
            String sheetName
    ) throws Exception {

        GoogleSheetService service
                = new GoogleSheetService(sheetId, sheetName);

        String json = service.fetchSheetData(accessToken);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode valuesNode = root.get("values");

        List<List<String>> rows = new ArrayList<>();

        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode rowNode : valuesNode) {
                List<String> row = new ArrayList<>();
                for (JsonNode cell : rowNode) {
                    row.add(cell.asText());
                }
                rows.add(row);
            }
        }

        return rows;
    }

    /**
     * Updates a specific row in the sheet (1-based row index). This performs a
     * PATCH-style overwrite for that row.
     */
    public void updateRow(
            String accessToken,
            String sheetName,
            int rowNumber,
            List<String> rowValues
    ) throws Exception {

        String range
                = sheetName + "!A" + rowNumber;

        String encodedRange
                = URLEncoder.encode(range, StandardCharsets.UTF_8);

        String url
                = "https://sheets.googleapis.com/v4/spreadsheets/"
                + sheetId
                + "/values/"
                + encodedRange
                + "?valueInputOption=RAW";

        Map<String, Object> body = new HashMap<>();
        body.put("values", List.of(rowValues));

        ObjectMapper mapper = new ObjectMapper();
        String requestBody = mapper.writeValueAsString(body);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response
                = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to update row " + rowNumber
                    + ". HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }
    }
}
