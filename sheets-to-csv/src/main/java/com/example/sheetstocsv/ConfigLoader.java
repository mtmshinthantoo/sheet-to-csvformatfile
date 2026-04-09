package com.example.sheetstocsv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class ConfigLoader {

    private final Properties properties = new Properties();
    private final File configFile;

    public ConfigLoader() throws IOException {
        this.configFile = new File("sheets-to-csv/src/main/resources/application.properties");

        try (InputStream in = new FileInputStream(configFile)) {
            properties.load(in);
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public Boolean hasValue(String key) {
        String value = properties.getProperty(key);
        return value != null && !value.isBlank();
    }

    public void setAndSave(String key, String value) throws IOException {
        properties.setProperty(key, value);
        try (OutputStream out = new FileOutputStream(configFile)) {
            properties.store(out, "Updated " + key);
        }
    }
}
