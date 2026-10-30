package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Hub(String hubId, String province, String sortingCenter, Boolean active, List<String> aliases) {
    public Hub {
        aliases = aliases == null ? List.of() : aliases;
    }
}
