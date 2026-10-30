package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Synchronous REST version of StageSource (replaced by the MQ subscriber in stage 3). */
public class DelayStageClient implements StageSource {

    private static final String BASE_URL = "http://localhost:7052";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public int stageFor(String hubId) throws IOException, InterruptedException {
        String url = BASE_URL + "/delay-stage/" + URLEncoder.encode(hubId, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("delay-stage-service returned " + response.statusCode());
        }
        return mapper.readTree(response.body()).path("stage").asInt(0);
    }
}
