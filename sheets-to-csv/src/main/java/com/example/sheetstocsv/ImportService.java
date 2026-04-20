package com.example.sheetstocsv;

import java.nio.file.Path;
import java.util.List;

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
     * Import CSV data and append it to a Google Sheet.
     *
     * @param csvPath Path to CSV file
     * @param accessToken OAuth access token
     * @param targetSheetName Sheet name to append into
     */
    public void importAppend(
            Path csvPath,
            String accessToken,
            String targetSheetName
    ) throws Exception {

        // Step 1: Read CSV file
        List<List<String>> values = csvReaderService.readCsv(csvPath);

        if (values == null || values.isEmpty()) {
            System.out.println("CSV file is empty. Nothing to append.");
            return;
        }

        // Step 2: Append values to Google Sheet
        googleSheetService.appendValues(
                accessToken,
                targetSheetName,
                values
        );

        System.out.println("Import -> Append completed successfully.");
    }

    /**
     * Import CSV data and update/overwrite a Google Sheet. (Reserved for future
     * implementation)
     */
    public void importUpdate(
            Path csvPath,
            String accessToken,
            String targetSheetName
    ) {
        throw new UnsupportedOperationException(
                "Import -> Update is not implemented yet."
        );
    }
}
