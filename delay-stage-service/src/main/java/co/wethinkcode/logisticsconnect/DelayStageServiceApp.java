package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelayStageServiceApp {

    private static final Logger LOG = LoggerFactory.getLogger(DelayStageServiceApp.class);
    private static final Map<String, Integer> STAGES = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        StagePublisher publisher = new StagePublisher();
        Runtime.getRuntime().addShutdownHook(new Thread(publisher::close));

        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").trim().toUpperCase();
            ctx.json(new DelayStage(hubId, STAGES.getOrDefault(hubId, 0)));
        });

        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").trim().toUpperCase();
            StageUpdate update = ctx.bodyAsClass(StageUpdate.class);
            if (update.stage() == null || update.stage() < 0 || update.stage() > 8) {
                ctx.status(400).json(Map.of("error", "stage must be an integer from 0 to 8"));
                return;
            }

            Integer previous = STAGES.put(hubId, update.stage());
            int before = previous == null ? 0 : previous;

            // Only publish when the stage actually changed.
            if (before != update.stage()) {
                try {
                    publisher.publish(hubId, update.stage());
                    LOG.info("Published stage {} for {} to the topic", update.stage(), hubId);
                } catch (Exception e) {
                    // The stage is saved, so the REST call still succeeds; consumers just miss this update.
                    LOG.warn("Stage saved but could not publish to MQ: {}", e.getMessage());
                }
            }
            ctx.json(new DelayStage(hubId, update.stage()));
        });
    }
}
