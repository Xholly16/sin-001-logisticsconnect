package co.wethinkcode.logisticsconnect;

public record EtaResponse(String hubId, String sortingCenter, String province,
                          int delayStage, String etaFrom, String etaTo) {
}
