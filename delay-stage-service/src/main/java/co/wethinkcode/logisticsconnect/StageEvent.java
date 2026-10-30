package co.wethinkcode.logisticsconnect;

/** The message published to package-status-topic whenever a hub's delay stage changes. */
public record StageEvent(String hubId, int stage, String timestamp) {
}
