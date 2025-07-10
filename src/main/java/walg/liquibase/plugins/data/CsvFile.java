package walg.liquibase.plugins.data;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import liquibase.Scope;
import liquibase.resource.Resource;
import liquibase.resource.ResourceAccessor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvFile {

    private final Resource resource;
    private List<String> headers = null;
    private List<List<String>> rows = null;

    public CsvFile(String file) {
        ResourceAccessor resourceAccessor = Scope.getCurrentScope().getResourceAccessor();
        try {
            resource = resourceAccessor.getExisting(file);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public List<String> getHeaders() {
        if (headers == null) {
            try (InputStream inputStream = resource.openInputStream();
                 Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                 CSVReader csvReader = new CSVReader(reader)) {
                headers = Arrays.asList(csvReader.readNext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return headers;
    }

    public List<List<String>> getRows() {
        if (rows == null) {
            try (InputStream inputStream = resource.openInputStream();
                 Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                 CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
                List<List<String>> temp = new ArrayList<>();
                for (String[] row : csvReader.readAll()) {
                    temp.add(Arrays.asList(row));
                }
                rows = temp;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return rows;
    }

    /**
     * Get the value in a specific column from all rows. The column is specified by providing the header value.
     * @param header
     * @return
     */

    public List<String> getValues(String header) {
        int columnIndex = getHeaders().indexOf(header);
        return getRows().stream().map(row -> row.get(columnIndex)).toList();
    }
}
