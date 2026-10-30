package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class HubClient {

    private static final String BASE_URL = "http://localhost:7051";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<HubInfo> find(String hubId) throws IOException, InterruptedException {
        String url = BASE_URL + "/hubs/" + URLEncoder.encode(hubId, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("hub-service returned " + response.statusCode());
        }
        return Optional.of(mapper.readValue(response.body(), HubInfo.class));
    }
}
