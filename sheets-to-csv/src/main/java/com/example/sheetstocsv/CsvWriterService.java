package com.example.sheetstocsv;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CsvWriterService {

    public void writeCsv(String sheetJson, Path outputFilePath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(sheetJson);
        JsonNode valuesNode = rootNode.get("values");

        if (valuesNode == null || !valuesNode.isArray()) {
            throw new RuntimeException("Invalid sheet data: 'values' field is missing.");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath.toFile()))) {
            for (JsonNode rowNode : valuesNode) {
                StringBuilder line = new StringBuilder();

                for (int i = 0; i < rowNode.size(); i++) {
                    String cell = rowNode.get(i).asText();

                    cell = cell.replace("\"", "\"\"");

                    line.append("\"").append(cell).append("\"");

                    if (i < rowNode.size() - 1) {
                        line.append(",");
                    }
                }
                writer.println(line);
            }
        }
    }
}
