package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Same shape as the message delay-stage-service publishes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StageEvent(String hubId, int stage, String timestamp) {
}
