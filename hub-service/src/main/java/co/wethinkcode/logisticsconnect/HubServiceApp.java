package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class HubServiceApp {

    private static final String INGESTION_URL = "http://localhost:7050/hubs";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        List<Hub> hubs = loadHubs();

        Javalin app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> ctx.json(hubs));

        app.get("/hubs/{hubId}", ctx -> {
            String id = ctx.pathParam("hubId").trim().toUpperCase();
            Optional<Hub> hub = hubs.stream()
                    .filter(h -> h.hubId().equals(id) || h.aliases().contains(id))
                    .findFirst();
            if (hub.isPresent()) {
                ctx.json(hub.get());
            } else {
                ctx.status(404).json(Map.of("error", "Hub not found: " + id));
            }
        });

        app.get("/provinces", ctx -> ctx.json(
                hubs.stream().map(Hub::province).filter(Objects::nonNull).distinct().sorted().toList()));
    }

    /** Loads the cleaned hubs from ingestion-service; falls back to sample data if it is not reachable. */
    private static List<Hub> loadHubs() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(INGESTION_URL)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<Hub> hubs = MAPPER.readValue(response.body(), new TypeReference<List<Hub>>() { });
                System.out.println("Loaded " + hubs.size() + " hubs from ingestion-service");
                return hubs;
            }
            System.err.println("ingestion-service returned " + response.statusCode());
        } catch (Exception e) {
            System.err.println("Could not reach ingestion-service: " + e.getMessage());
        }
        System.err.println("Using built-in sample hubs instead");
        return List.of(
                new Hub("H-500", "Gauteng", "Johannesburg Central", true, List.of()),
                new Hub("H-501", "Western Cape", "Cape Town Port", true, List.of()));
    }
}
