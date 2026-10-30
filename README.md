# LogisticsConnect

Supply chain parcel delivery hub and transit delay tracking, built as small independent
services that grow from synchronous REST calls to asynchronous ActiveMQ messaging.

All classes live in one flat package, `co.wethinkcode.logisticsconnect`. Every folder is
its own Maven project (no parent pom). Requirements: Java 17+, Maven 3.8+, Docker.

## Architecture

    client --POST stage--> delay-stage-service (7052)
                                  |
                                  | publishes StageEvent on change
                                  v
                       ActiveMQ topic: package-status-topic
                          |                         |
                          v                         v
                 transit-service (7053)        alertbot (7054)
                 keeps latest stage per hub    posts a (simulated) alert
                          |                    when a hub reaches stage 5+
                          | GET /hubs/{id}  (REST)
                          v
                   hub-service (7051)
                          ^
                          | GET /hubs  (loaded at startup)
                   ingestion-service (7050)

| Service | Folder | Port | Role |
|---|---|---|---|
| ingestion-service | `ingestion-service/` | 7050 | Should clean `hubs-global.csv` (see Status) |
| hub-service | `hub-service/` | 7051 | Hubs, sorting centers and provinces; resolves hub aliases |
| delay-stage-service | `delay-stage-service/` | 7052 | Tracks each hub's delay stage (0-8) and publishes changes |
| transit-service | `transit-service/` | 7053 | Calculates ETA windows from hub data and delay stage |
| alertbot | `alertbot/` | 7054 | Subscribes to stage changes and posts simulated alerts |
| common | `common/` | - | `docker-compose.yml` for the ActiveMQ broker |

## Status

| Stage | State |
|---|---|
| 1. Clean `hubs-global.csv` | NOT DONE. `ingestion-service` only exposes `/health`. `hub-service` tries `GET :7050/hubs` at startup and falls back to two built-in sample hubs (H-500, H-501). |
| 2. REST services | Done: hub-service, delay-stage-service and transit-service all expose real endpoints. |
| 3. MQ decoupling | Done: delay-stage-service publishes, transit-service subscribes. The REST call between them was removed. |
| 4. AlertBot (stretch) | Done: alerts when a hub's stage rises to 5 or above. |

## Run

Open one terminal per service, from the project root. Start the broker first.

    docker compose -f common/docker-compose.yml up -d

    cd hub-service && mvn -q package && java -jar target/hub-service.jar
    cd delay-stage-service && mvn -q package && java -jar target/delay-stage-service.jar
    cd transit-service && mvn -q package && java -jar target/transit-service.jar
    cd alertbot && mvn -q package && java -jar target/alertbot.jar

Start transit-service and alertbot before posting stages (see Design decisions, cold start).
ActiveMQ web console: http://localhost:8161 (admin/admin).

## API

hub-service (7051)

- `GET /hubs` - all hubs
- `GET /hubs/{hubId}` - one hub by ID or alias, case-insensitive; 404 if unknown
- `GET /provinces` - distinct province names

delay-stage-service (7052)

- `GET /delay-stage/{hubId}` - `{ "hubId": "H-501", "stage": 3 }` (unknown hubs are stage 0)
- `POST /delay-stage/{hubId}` with `{ "stage": 5 }` - stores the stage, publishes if it changed; 400 unless 0-8

transit-service (7053)

- `GET /eta/{hubId}` - `{ hubId, sortingCenter, province, delayStage, etaFrom, etaTo }`
  - ETA starts 24 hours from now, plus 6 hours per delay stage; the window is 12 hours wide
  - 404 for an unknown hub, 503 if hub-service is unreachable

alertbot (7054)

- `GET /alerts` - every alert posted so far

Topic `package-status-topic` message:

    { "hubId": "H-501", "stage": 5, "timestamp": "2026-10-30T10:15:00Z" }

## Design decisions

**REST for hub data, MQ for delay stages.** transit-service needs a hub's details to answer a
request right now, and hub data is stable reference data, so a plain request/response call fits.
A stage change is an event that more than one service cares about (transit-service and alertbot).
With a topic, delay-stage-service does not know who is listening, a new consumer needs no change
to it, and transit-service keeps answering ETAs even when delay-stage-service is down.

**`StageSource` interface.** transit-service reads stages through this interface. Swapping the
REST client for the MQ subscriber (`MqStageSource`) was a one-line change in `TransitServiceApp`.

**Publish only on change.** Posting the same stage twice sends one message, so consumers are not
spammed with duplicates.

**Lazy publisher connection.** delay-stage-service connects to the broker on its first publish.
If the broker is down the stage is still saved and the POST still succeeds, but consumers miss
that update (a warning is logged). The connection is rebuilt on the next publish.

**Alert on crossing, not on level.** alertbot alerts when a hub rises from below the threshold
(5) to the threshold or above, so stages 6, 7 and 8 do not repeat the alert. The "post" is a log
line and an entry in `/alerts`; a real version would call a social API or webhook there.

## Known limitations

- Stages are held in memory, so delay-stage-service forgets everything on restart.
- Cold start: a topic only delivers to connected subscribers. After a restart, transit-service
  treats every hub as stage 0 until the next stage change.
- `MqConfig` and `StageEvent` are copied into each service because there is no shared module.
- Ports and the broker URL are hard-coded.
- `HttpClient` calls in transit-service have no timeout or retry.
- Stage 1 (CSV cleaning and duplicate handling) is not implemented yet.

## With more time

- Implement ingestion-service and decide a duplicate strategy (for example, merge rows that
  normalise to the same hub ID and keep the most complete record).
- Fix the cold start with a durable subscription, or have transit-service fetch current stages
  once at startup.
- Persist stages, move config to environment variables, and add a shared module for the
  duplicated classes.
- Add JUnit tests, starting with `AlertService` (it has no messaging dependency).

## Test

No automated tests yet. Manual end-to-end check, with all services and the broker running:

    curl.exe http://localhost:7053/eta/H-501
    Invoke-RestMethod -Method Post -Uri http://localhost:7052/delay-stage/H-501 -ContentType "application/json" -Body '{"stage":6}'
    curl.exe http://localhost:7053/eta/H-501
    curl.exe http://localhost:7054/alerts
    curl.exe http://localhost:7053/eta/H-999

Expected: the stage goes 0 to 6 and etaFrom moves 36 hours later, one alert appears, and H-999 returns 404.
