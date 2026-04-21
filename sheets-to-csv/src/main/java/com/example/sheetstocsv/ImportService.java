package com.example.sheetstocsv;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ImportService {

    private final CsvReaderService csvReaderService;
    private final GoogleSheetService googleSheetService;

    public ImportService(
            CsvReaderService csvReaderService,
            GoogleSheetService googleSheetService
    ) {
        this.csvReaderService = csvReaderService;
        this.googleSheetService = googleSheetService;
    }

    /**
     * Import CSV and append data rows to a Google Sheet.
     *
     * Rules: 1. CSV must not be empty 2. CSV headers must match sheet headers
     * 3. CSV header row is NOT appended
     */
    public void importAppend(
            Path csvPath,
            String accessToken,
            String targetSheetName
    ) throws Exception {

        // ------------------ Read CSV ------------------
        List<List<String>> csvRows = csvReaderService.readCsv(csvPath);

        if (csvRows.isEmpty()) {
            throw new RuntimeException("CSV file is empty.");
        }

        List<String> csvHeaders = csvRows.get(0);

        // ------------------ Fetch Sheet Headers ------------------
        List<String> sheetHeaders
                = fetchSheetHeaders(accessToken, targetSheetName);

        // ------------------ Validate Headers ------------------
        if (!headersMatch(csvHeaders, sheetHeaders)) {
            throw new RuntimeException(
                    "CSV headers do not match Google Sheet headers. Import aborted."
            );
        }

        // ------------------ Remove Header Row ------------------
        List<List<String>> dataRows
                = new ArrayList<>(csvRows.subList(1, csvRows.size()));

        if (dataRows.isEmpty()) {
            System.out.println("CSV contains only header row. Nothing to append.");
            return;
        }

        // ------------------ Append ------------------
        googleSheetService.appendValues(
                accessToken,
                targetSheetName,
                dataRows
        );

        System.out.println(
                "Import successful. Appended " + dataRows.size() + " rows."
        );
    }

    // ============================================================
    // ===================== Helper Methods =======================
    // ============================================================
    /**
     * Fetches the header row (row 1) from the target Google Sheet.
     */
    private List<String> fetchSheetHeaders(
            String accessToken,
            String sheetName
    ) throws Exception {

        GoogleSheetService headerService
                = new GoogleSheetService(
                        getSheetIdFromService(),
                        sheetName + "!1:1"
                );

        String json = headerService.fetchSheetData(accessToken);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode valuesNode = root.get("values");

        if (valuesNode == null || !valuesNode.isArray() || valuesNode.isEmpty()) {
            throw new RuntimeException("Target sheet has no header row.");
        }

        List<String> headers = new ArrayList<>();
        for (JsonNode cell : valuesNode.get(0)) {
            headers.add(cell.asText().trim());
        }

        return headers;
    }

    /**
     * Compares CSV headers with sheet headers.
     */
    private boolean headersMatch(
            List<String> csvHeaders,
            List<String> sheetHeaders
    ) {
        if (csvHeaders.size() != sheetHeaders.size()) {
            return false;
        }

        for (int i = 0; i < csvHeaders.size(); i++) {
            if (!csvHeaders.get(i).trim()
                    .equalsIgnoreCase(sheetHeaders.get(i).trim())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extract sheetId from existing GoogleSheetService safely.
     */
    private String getSheetIdFromService() {
        try {
            var field = GoogleSheetService.class.getDeclaredField("sheetId");
            field.setAccessible(true);
            return (String) field.get(googleSheetService);
        } catch (Exception e) {
            throw new RuntimeException("Unable to access sheetId.", e);
        }
    }
}
