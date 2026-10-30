package co.wethinkcode.logisticsconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Decides when a stage update deserves a public alert, and "posts" it (simulated). */
public class AlertService {

    /** An alert is raised when a hub's stage rises from below this value to this value or above. */
    public static final int THRESHOLD = 5;
    private static final long HOURS_PER_STAGE = 6;

    private static final Logger LOG = LoggerFactory.getLogger(AlertService.class);

    private final Map<String, Integer> lastStage = new ConcurrentHashMap<>();
    private final List<Alert> alerts = new CopyOnWriteArrayList<>();

    // synchronized so the "previous vs new" check and the update happen together
    public synchronized void onStageUpdate(StageEvent event) {
        String hubId = event.hubId().trim().toUpperCase();
        int previous = lastStage.getOrDefault(hubId, 0);
        lastStage.put(hubId, event.stage());

        // Alert only on the crossing, so stages 6, 7, 8 don't spam the page.
        if (previous < THRESHOLD && event.stage() >= THRESHOLD) {
            String text = "Delay notice: hub " + hubId + " is at delay stage " + event.stage()
                    + " of 8. Parcels may arrive about " + (event.stage() * HOURS_PER_STAGE)
                    + " hours later than usual.";
            Alert alert = new Alert(hubId, event.stage(), text, Instant.now().toString());
            alerts.add(alert);
            postToSocialMedia(alert);
        }
    }

    /** Simulated: a real version would call the social platform's API or a webhook here. */
    private void postToSocialMedia(Alert alert) {
        LOG.info("[SIMULATED SOCIAL POST] {}", alert.message());
    }

    public List<Alert> alerts() {
        return List.copyOf(alerts);
    }
}
