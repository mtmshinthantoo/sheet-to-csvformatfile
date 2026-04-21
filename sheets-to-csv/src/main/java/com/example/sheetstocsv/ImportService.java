package com.example.sheetstocsv;

import java.nio.file.Path;
import java.util.*;

/**
 * ImportService
 *
 * APPEND:
 * - CSV headers must exactly match sheet headers
 * - Header row is removed
 *
 * UPDATE (PATCH semantics):
 * - Primary key = first column
 * - CSV headers must be a subset of sheet headers
 * - Update ONLY non-blank CSV values
 * - Blank / missing CSV values keep existing sheet data
 * - New rows appended ONLY if headers fully match
 * - Otherwise skipped with warning
 */
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

    // ========================= APPEND ===========================
    public void importAppend(
            Path csvPath,
            String accessToken,
            String sheetName
    ) throws Exception {

        List<List<String>> csvRows = csvReaderService.readCsv(csvPath);

        if (csvRows.isEmpty()) {
            throw new RuntimeException("CSV file is empty.");
        }

        List<String> csvHeaders = csvRows.get(0);
        List<List<String>> csvData = csvRows.subList(1, csvRows.size());

        if (csvData.isEmpty()) {
            System.out.println("CSV contains only header row. Nothing to append.");
            return;
        }

        List<List<String>> sheetRows =
                googleSheetService.fetchAllValues(accessToken, sheetName);

        if (sheetRows.isEmpty()) {
            throw new RuntimeException("Target sheet is empty.");
        }

        List<String> sheetHeaders = sheetRows.get(0);

        if (!headersExactlyMatch(csvHeaders, sheetHeaders)) {
            throw new RuntimeException(
                    "CSV headers do not exactly match sheet headers. Append aborted."
            );
        }

        googleSheetService.appendValues(
                accessToken,
                sheetName,
                csvData
        );

        System.out.println(
                "Append completed. Added " + csvData.size() + " rows."
        );
    }

    // ========================= UPDATE ===========================
    public void importUpdate(
            Path csvPath,
            String accessToken,
            String sheetName
    ) throws Exception {

        // ---------- Read CSV ----------
        List<List<String>> csvRows = csvReaderService.readCsv(csvPath);

        if (csvRows.isEmpty()) {
            throw new RuntimeException("CSV file is empty.");
        }

        List<String> csvHeaders = csvRows.get(0);
        List<List<String>> csvData = csvRows.subList(1, csvRows.size());

        if (csvData.isEmpty()) {
            System.out.println("CSV contains only header row. Nothing to update.");
            return;
        }

        // ---------- Fetch Sheet ----------
        List<List<String>> sheetRows =
                googleSheetService.fetchAllValues(accessToken, sheetName);

        if (sheetRows.isEmpty()) {
            throw new RuntimeException("Target sheet is empty.");
        }

        List<String> sheetHeaders = sheetRows.get(0);
        List<List<String>> sheetData = sheetRows.subList(1, sheetRows.size());

        // ---------- Primary Key Validation ----------
        if (!csvHeaders.get(0).equalsIgnoreCase(sheetHeaders.get(0))) {
            throw new RuntimeException(
                    "Primary key mismatch. CSV first column must be '"
                            + sheetHeaders.get(0) + "'."
            );
        }

        // ---------- Header Subset Validation ----------
        Map<String, Integer> sheetHeaderIndex = buildHeaderIndex(sheetHeaders);

        for (String csvHeader : csvHeaders) {
            if (!sheetHeaderIndex.containsKey(csvHeader.toLowerCase())) {
                throw new RuntimeException(
                        "CSV header '" + csvHeader + "' does not exist in sheet."
                );
            }
        }

        // ---------- Build Sheet PK Index ----------
        Map<String, Integer> sheetPkToRowNumber = new HashMap<>();

        for (int i = 0; i < sheetData.size(); i++) {
            List<String> row = sheetData.get(i);
            if (!row.isEmpty()) {
                sheetPkToRowNumber.put(row.get(0), i + 2); // +2 (header + 1-based)
            }
        }

        // ---------- Detect Duplicate CSV PK ----------
        Set<String> seenCsvKeys = new HashSet<>();
        for (List<String> row : csvData) {
            String pk = row.get(0);
            if (!seenCsvKeys.add(pk)) {
                throw new RuntimeException(
                        "Duplicate primary key in CSV: " + pk
                );
            }
        }

        // ---------- Process Rows ----------
        List<List<String>> rowsToAppend = new ArrayList<>();

        for (List<String> csvRow : csvData) {

            String pk = csvRow.get(0);

            if (pk == null || pk.isBlank()) {
                System.out.println("Warning: skipping row with empty primary key.");
                continue;
            }

            // ---------- UPDATE (PATCH SAFE) ----------
            if (sheetPkToRowNumber.containsKey(pk)) {

                int rowNumber = sheetPkToRowNumber.get(pk);
                List<String> existingRow =
                        new ArrayList<>(sheetRows.get(rowNumber - 1));

                for (int i = 1; i < csvHeaders.size(); i++) {
                    String newValue = csvRow.get(i);

                    // PATCH RULE:
                    // Only update when CSV provides a NON-BLANK value
                    if (newValue != null && !newValue.isBlank()) {
                        String header = csvHeaders.get(i);
                        int sheetColIndex =
                                sheetHeaderIndex.get(header.toLowerCase());
                        existingRow.set(sheetColIndex, newValue);
                    }
                }

                googleSheetService.updateRow(
                        accessToken,
                        sheetName,
                        rowNumber,
                        existingRow
                );
            }
            // ---------- APPEND (CONDITIONAL) ----------
            else {
                if (headersExactlyMatch(csvHeaders, sheetHeaders)) {
                    rowsToAppend.add(csvRow);
                } else {
                    System.out.println(
                            "Warning: skipping append for primary key '"
                                    + pk
                                    + "' due to incomplete headers."
                    );
                }
            }
        }

        // ---------- Final Append ----------
        if (!rowsToAppend.isEmpty()) {
            googleSheetService.appendValues(
                    accessToken,
                    sheetName,
                    rowsToAppend
            );
            System.out.println(
                    "Update completed. Appended " + rowsToAppend.size() + " new rows."
            );
        } else {
            System.out.println("Update completed. No rows appended.");
        }
    }

    // ========================= HELPERS ==========================
    private boolean headersExactlyMatch(
            List<String> a,
            List<String> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).trim().equalsIgnoreCase(b.get(i).trim())) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> buildHeaderIndex(
            List<String> headers
    ) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(headers.get(i).toLowerCase(), i);
        }
        return index;
    }
}