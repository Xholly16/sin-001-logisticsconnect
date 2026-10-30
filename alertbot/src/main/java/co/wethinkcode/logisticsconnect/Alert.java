package co.wethinkcode.logisticsconnect;

public record Alert(String hubId, int stage, String message, String postedAt) {
}
