package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelayStageServiceApp {

    private static final Map<String, Integer> STAGES = new ConcurrentHashMap<>();

    public static void main(String[] args) {
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
            STAGES.put(hubId, update.stage());
            ctx.json(new DelayStage(hubId, update.stage()));
        });
    }
}
