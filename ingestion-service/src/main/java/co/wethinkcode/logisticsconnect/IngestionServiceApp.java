package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) throws Exception {
        List<String[]> rawRows = HubCsvLoader.load("/hubs-global.csv");
        List<Hub> hubs = HubCleaner.clean(rawRows);
        System.out.println("Cleaned " + rawRows.size() + " rows into " + hubs.size() + " hubs");

        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> ctx.json(hubs));
    }
}
