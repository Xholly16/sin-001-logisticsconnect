package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads the raw rows (header removed) from a CSV file on the classpath. */
public class HubCsvLoader {

    public static List<String[]> load(String resource) throws IOException, CsvException {
        try (InputStream in = HubCsvLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resource);
            }
            try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String[]> rows = reader.readAll();
                return rows.isEmpty() ? rows : rows.subList(1, rows.size()); // skip the header row
            }
        }
    }
}
