package co.wethinkcode.logisticsconnect;

import java.util.List;

/** A cleaned hub. Same JSON shape that hub-service expects from GET /hubs. */
public record Hub(String hubId, String province, String sortingCenter, Boolean active, List<String> aliases) {
}
