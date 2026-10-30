package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class TransitServiceApp {

    private static final long BASE_HOURS = 24;
    private static final long HOURS_PER_STAGE = 6;
    private static final long WINDOW_HOURS = 12;

    public static void main(String[] args) throws Exception {
        HubClient hubs = new HubClient();
        MqStageSource mqStages = new MqStageSource();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqStages.close();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }));
        StageSource stages = mqStages;

        Javalin app = Javalin.create().start(7053);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/eta/{hubId}", ctx -> {
            String requested = ctx.pathParam("hubId").trim().toUpperCase();
            Optional<HubInfo> hub = hubs.find(requested);
            if (hub.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Hub not found: " + requested));
                return;
            }
            String hubId = hub.get().hubId(); // canonical ID (aliases resolve to it)
            int stage = stages.stageFor(hubId);

            Instant from = Instant.now().plus(Duration.ofHours(BASE_HOURS + stage * HOURS_PER_STAGE));
            Instant to = from.plus(Duration.ofHours(WINDOW_HOURS));

            ctx.json(new EtaResponse(hubId, hub.get().sortingCenter(), hub.get().province(),
                    stage, from.toString(), to.toString()));
        });

        app.exception(IOException.class, (e, ctx) ->
                ctx.status(503).json(Map.of("error", "Upstream service unavailable: " + e.getMessage())));
    }
}
