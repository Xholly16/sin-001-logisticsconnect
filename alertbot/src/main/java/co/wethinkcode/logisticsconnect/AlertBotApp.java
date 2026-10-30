package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

public class AlertBotApp {

    public static void main(String[] args) throws Exception {
        AlertService alertService = new AlertService();
        StageSubscriber subscriber = new StageSubscriber(alertService);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                subscriber.close();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }));

        Javalin app = Javalin.create().start(7054);

        app.get("/health", ctx -> ctx.result("OK"));

        // Lets you check what has been "posted" without reading logs.
        app.get("/alerts", ctx -> ctx.json(alertService.alerts()));
    }
}
