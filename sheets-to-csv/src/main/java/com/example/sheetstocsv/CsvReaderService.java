package com.example.sheetstocsv;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvReaderService {

    /**
     * Reads a CSV file and returns rows as List<List<String>>.
     *
     * @param csvPath path to CSV file
     * @return parsed CSV data
     * @throws IOException if file cannot be read
     */
    public List<List<String>> readCsv(Path csvPath) throws IOException {

        List<List<String>> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }

        return rows;
    }

    /**
     * Parses a single CSV line correctly.
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '\"') {
                // Handle escaped quote
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        result.add(current.toString());
        return result;
    }
}
