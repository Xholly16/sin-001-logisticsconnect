package co.wethinkcode.logisticsconnect;

import java.io.IOException;

/** Where transit-service gets a hub's current delay stage from. */
public interface StageSource {
    int stageFor(String hubId) throws IOException, InterruptedException;
}
