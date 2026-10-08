# ledger-service

`ledger-service` is the thin, product-agnostic gateway to TigerBeetle in the Send Money POC v2. It owns the chart of accounts, posts multi-leg transfers as one atomic linked batch, and serves balances. It knows nothing about Send Money: a posting is a list of legs (debit, credit, amount, code), and future products reuse the service unchanged. `transaction-service` is its only caller and treats `200 POSTED` as the commit point. Posting and transfer IDs are deterministic, so every call is idempotent and safe to retry. The service is stateless; TigerBeetle is its only store.

Spec: [`docs/Send Money POC v2 — SRS & Design Spec.md`](docs/Send%20Money%20POC%20v2%20%E2%80%94%20SRS%20%26%20Design%20Spec.md). Choices where the spec is silent or adapted: [`docs/decisions.md`](docs/decisions.md). Build status: [`PROGRESS.md`](PROGRESS.md).

## Tech stack

| Component | Version |
| --- | --- |
| Java (Eclipse Temurin) | 25 |
| Spring Boot (Spring Framework 7.0.9), Spring MVC on virtual threads | 4.1.1 |
| TigerBeetle Java client and server image `ghcr.io/tigerbeetle/tigerbeetle` | 0.17.9 (must match) |
| Micrometer + Prometheus registry | Boot-managed |
| Gradle (Kotlin DSL, version catalog `gradle/libs.versions.toml`) | 9.7.1 (wrapper) |
| Testcontainers | 2.0.5 (Boot-managed) |
| API contract | OpenAPI 3.1, `openapi/ledger-api.yaml` |

## Project layout

Hexagonal: the domain has no Spring or TigerBeetle types; the API and the TigerBeetle adapter depend on it, never the other way round.

```
src/main/java/com/bracits/ledgerservice/
  api/                      HTTP layer: ApiConstants
    account/                accounts + balance controller, request/response records, mapper
    posting/                postings + lookup controller, records, mapper
    funding/                fundings controller (profile "test")
    error/                  ErrorCode, RFC 9457 ProblemDetail mapping and advice
  application/
    posting/                PostingService (@ConcurrencyLimit, MDC postingId, timer)
    account/                AccountService, ChartOfAccountsBootstrap
  domain/
    posting/                Posting, Leg, PostingOutcome, RejectionCode, TransferIds, ...
    account/                Account, AccountFlag, Balance, SystemAccount, ...
  port/out/                 LedgerStore (the port to the ledger), LedgerUnavailableException
  adapter/out/tigerbeetle/  FencedClientHolder, TigerBeetleLedgerStore, mappers, health indicator
  config/                   @ConfigurationProperties records, ResilienceConfig
openapi/ledger-api.yaml     authoritative contract, served at GET /openapi.yaml
```

## Build and test

```bash
./gradlew clean build
```

This compiles, runs the unit, slice and contract tests, and runs the integration test against a real TigerBeetle in a Testcontainers container. The integration test needs a reachable Docker daemon; without one it is skipped automatically (`@Testcontainers(disabledWithoutDocker = true)`).

## Run with Docker

`docker-compose.yml` runs one TigerBeetle replica (development mode: `--development --cache-grid=256MiB`) and one `ledger-service` instance on port 8081. The full POC stack adds `transaction-service`, PostgreSQL, RabbitMQ, HAProxy with two instances of each service, Prometheus, Grafana and Jaeger.

```bash
# 1. Once: create the TigerBeetle data file in the named volume.
docker compose --profile init run --rm tigerbeetle-format

# 2. Build and start.
docker compose up -d --build

# 3. Wait until ready (the container also reports "healthy" in `docker compose ps`).
curl -fsS http://localhost:8081/actuator/health/readiness
# {"status":"UP"}

# Stop (data is kept):
docker compose down

# Stop and delete the data. You must run step 1 again afterwards.
docker compose down -v
```

Formatting an existing data file fails, so step 1 is needed only on first use and after `down -v`.

The JVM options default to the P16 set (generational ZGC, heap = 75% of the 1 GiB container limit with `Xms == Xmx`, `AlwaysPreTouch`, `ExitOnOutOfMemoryError`). Override them with `LEDGER_JAVA_OPTS="..." docker compose up -d`.

On macOS, Docker Desktop needs the `IPC_LOCK` capability and an unlimited `memlock` ulimit for TigerBeetle (otherwise `error: SystemResources`); the compose file sets both, plus `seccomp=unconfined` for io_uring.

`ledger-service` also runs with `seccomp=unconfined`: on Linux the TigerBeetle **Java client** uses io_uring too. Under Docker's default seccomp profile the native client panics with `io_uring is not available` and takes the JVM down. Any other deployment (Kubernetes, etc.) must allow io_uring for the service as well.

## Run locally

TigerBeetle in Docker, the service on the host:

```bash
docker compose --profile init run --rm tigerbeetle-format   # once
docker compose up -d tigerbeetle

POC_TIGERBEETLE_ADDRESSES=127.0.0.1:3000 SPRING_PROFILES_ACTIVE=test ./gradlew bootRun
```

## API

Internal network only. JSON over HTTP/1.1 keep-alive. Errors are RFC 9457 Problem Details (`application/problem+json`) with a machine-readable `code`. Account and posting IDs are 128-bit values written as UUID strings; `00000000-0000-0000-0000-0000000000c8` is id 200.

| Method and path | Purpose | Responses |
| --- | --- | --- |
| `POST /internal/v1/accounts` | Create an account `{accountId, code, flags, userData64}` | 201 created · 200 already exists (identical) · 409 `ACCOUNT_CONFLICT` |
| `POST /internal/v1/postings` | Atomic multi-leg posting (1–8 legs) | 200 POSTED · 422 REJECTED · 409 conflict · 500 ledger error · 503 outcome unknown / overloaded |
| `GET /internal/v1/postings/{postingId}?legs=n` | Look up a posting's n legs | 200 `{status: POSTED \| NOT_FOUND}` |
| `GET /internal/v1/accounts/{accountId}/balance` | Balance | 200 `{debitsPosted, creditsPosted, debitsPending, creditsPending, available}` · 404 |
| `POST /internal/v1/fundings` *(profile `test`)* | Issuance → account, single transfer (code 1) | 200 POSTED (same responses as postings) |
| `GET /actuator/health/liveness`, `/actuator/health/readiness` | Probes; readiness = TigerBeetle reachable | 200 UP · 503 DOWN |
| `GET /actuator/prometheus` | Metrics | 200 |
| `GET /openapi.yaml` | The OpenAPI 3.1 contract | 200 |

Validation (400 `VALIDATION_FAILED`): 1–8 legs, every amount > 0, codes 1–65535, every account ID non-zero, debit ≠ credit, and the low byte of `postingId` / `fundingId` must be `00` (it carries the leg index: transfer id of leg n = `postingId | n`, n = 1..8).

### Examples

```bash
LEDGER=http://localhost:8081

# Create two customer wallets: code 100, cannot be overdrawn, userData64 = walletId.
curl -sS -X POST $LEDGER/internal/v1/accounts -H 'Content-Type: application/json' -d '{
  "accountId": "00000000-0000-0000-0000-000000010001", "code": 100,
  "flags": ["DEBITS_MUST_NOT_EXCEED_CREDITS"], "userData64": 10001 }'
# 201 {"accountId":"00000000-0000-0000-0000-000000010001","status":"CREATED"}
# (the same request again: 200 ... "status":"EXISTS")

curl -sS -X POST $LEDGER/internal/v1/accounts -H 'Content-Type: application/json' -d '{
  "accountId": "00000000-0000-0000-0000-000000010002", "code": 100,
  "flags": ["DEBITS_MUST_NOT_EXCEED_CREDITS"], "userData64": 10002 }'

# Fund the sender with 2,000.00 BDT (200000 poisha) from e-money issuance (profile "test").
curl -sS -X POST $LEDGER/internal/v1/fundings -H 'Content-Type: application/json' -d '{
  "fundingId": "0192f5a4-7b3c-7e01-9a2b-3c4d5e6f1000",
  "accountId": "00000000-0000-0000-0000-000000010001", "amount": 200000 }'
# 200 {"postingId":"0192f5a4-7b3c-7e01-9a2b-3c4d5e6f1000","status":"POSTED","replay":false,"timestamp":...}

# Send Money: 4 legs (principal, fee income, VAT, commission) in one atomic linked batch.
curl -sS -X POST $LEDGER/internal/v1/postings -H 'Content-Type: application/json' -d '{
  "postingId": "0192f5a4-7b3c-7e01-9a2b-3c4d5e6f7000", "product": 1, "userData64": 10001,
  "legs": [
    {"debit": "00000000-0000-0000-0000-000000010001", "credit": "00000000-0000-0000-0000-000000010002", "amount": 100000, "code": 10},
    {"debit": "00000000-0000-0000-0000-000000010001", "credit": "00000000-0000-0000-0000-0000000000c8", "amount": 348, "code": 11},
    {"debit": "00000000-0000-0000-0000-000000010001", "credit": "00000000-0000-0000-0000-0000000000d2", "amount": 65, "code": 12},
    {"debit": "00000000-0000-0000-0000-000000010001", "credit": "00000000-0000-0000-0000-0000000000dc", "amount": 87, "code": 13}
  ] }'
# 200 {"postingId":"0192f5a4-7b3c-7e01-9a2b-3c4d5e6f7000","status":"POSTED","replay":false,"timestamp":1791350858928000000}
# The identical request again: 200 ... "replay":true with the original timestamp.

# Look up the posting (4 legs).
curl -sS "$LEDGER/internal/v1/postings/0192f5a4-7b3c-7e01-9a2b-3c4d5e6f7000?legs=4"
# 200 {"postingId":"0192f5a4-7b3c-7e01-9a2b-3c4d5e6f7000","status":"POSTED","timestamp":1791350858928000000}

# Sender balance: 200000 - 100500 = 99500.
curl -sS $LEDGER/internal/v1/accounts/00000000-0000-0000-0000-000000010001/balance
# 200 {"accountId":"...010001","debitsPosted":100500,"creditsPosted":200000,"debitsPending":0,"creditsPending":0,"available":99500}
```

A rejected posting (for example, the sender cannot cover the legs):

```json
422 Content-Type: application/problem+json
{"type":"urn:problem:ledger:INSUFFICIENT_FUNDS","title":"Insufficient funds","status":422,
 "detail":"...","code":"INSUFFICIENT_FUNDS","postingId":"...","postingStatus":"REJECTED","legIndex":1}
```

## How transaction-service connects

**Base URL:** `http://ledger-service:8081` on the compose network. In the full POC, call it through HAProxy: `http://haproxy:8090`.

**Contract:** `openapi/ledger-api.yaml` (OpenAPI 3.1) is authoritative; the running service also serves it at `GET /openapi.yaml`. Generate or contract-test the client against it.

**Fixed system accounts.** `ledger-service` creates these at start-up (idempotently) on ledger 1 with no flags. Their IDs are the account code in the low bytes of an otherwise-zero 128-bit ID. transaction-service reads them from its own configuration (spec §12):

| Account | Code | ID |
| --- | --- | --- |
| Fee income | 200 | `00000000-0000-0000-0000-0000000000c8` |
| VAT payable | 210 | `00000000-0000-0000-0000-0000000000d2` |
| Commission payable | 220 | `00000000-0000-0000-0000-0000000000dc` |
| E-money issuance | 900 | `00000000-0000-0000-0000-000000000384` |

```properties
poc.ledger.base-url=http://haproxy:8090
poc.ledger.connect-timeout=100ms
poc.ledger.read-timeout=1200ms
poc.ledger.total-budget=3s
poc.ledger.max-retries=2
poc.ledger.accounts.fee-income=00000000-0000-0000-0000-0000000000c8
poc.ledger.accounts.vat-payable=00000000-0000-0000-0000-0000000000d2
poc.ledger.accounts.commission-payable=00000000-0000-0000-0000-0000000000dc
poc.ledger.accounts.issuance=00000000-0000-0000-0000-000000000384
```

**Ledger and accounts.** Ledger `1` = BDT, amounts in poisha (`long` minor units). Customer wallets: code `100`, flags `["DEBITS_MUST_NOT_EXCEED_CREDITS"]`, `userData64` = `walletId`, `accountId` = `wallet.ledger_account_id`.

**Codes.** Send Money legs: `10` principal, `11` fee, `12` VAT, `13` commission, in that order (`legIndex` 1–4). Posting `product` (`user_data_32`) = 1 (SEND_MONEY), `userData64` = sender `walletId`. Funding uses code `1` (issuance → wallet).

**Posting IDs.** `postingId` = `txnId`, whose low byte is `00`. Leg n is stored as transfer `txnId | n`.

**Deadlines.** Inside the ledger, every TigerBeetle call has an 800 ms deadline; on timeout the service fences its client and answers 503 `LEDGER_TIMEOUT`. Set the caller's read timeout to **1200 ms** (connect 100 ms) so the ledger always gives up first, and keep the total ledger budget per request (retries included) at 3 s.

**Retry rules.**

- `200 POSTED` = committed: every leg is durably in TigerBeetle. Mark the transaction COMPLETED.
- Retry **only** on 503, an I/O error or a read timeout (at most 2 retries, 50 ms initial delay, ×2 backoff, jitter), and **always resend the identical body** (same `postingId`, same legs). A retry may go to any instance. 503s carry `Retry-After: 1`.
- `replay: true` on a 200 means the posting was already committed by an earlier attempt (your previous call timed out after the commit). It is still a success; the `timestamp` is the original one.
- **Never retry 422 or 409.** 422 is a definitive rejection: nothing was posted (the chain is all-or-nothing). Mark the transaction FAILED with the `code`.
- `422 PREVIOUSLY_REJECTED`: TigerBeetle permanently remembers a transfer ID that failed (for example with insufficient funds). It typically appears when a retry follows a lost 422. The posting never happened and never will; treat it as FAILED. A new attempt needs a new `postingId`.
- 409 `POSTING_CONFLICT` (same ID, different content) and 500 `LEDGER_ERROR` indicate a bug: leave the transaction INITIATED, alert, and let the repair worker resolve it.
- In-doubt repair: re-send the identical posting, or check it with `GET /internal/v1/postings/{postingId}?legs=n`. After a fence, the outcome of a timed-out posting is fixed, so a later answer is final.

## Error codes

Every error body carries `code`; posting errors also carry `postingId` and `postingStatus` (`REJECTED` or `UNKNOWN`), and leg errors `legIndex` (1-based).

| `code` | HTTP | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Malformed request or a domain rule broken (leg count, amount ≤ 0, code range, zero ID, low byte of postingId ≠ 0). Per-field details in `errors`. |
| `INSUFFICIENT_FUNDS` | 422 | A debited account would exceed its credits (`legIndex` = root-cause leg). Nothing posted. |
| `ACCOUNT_NOT_FOUND` | 422 | A leg's debit or credit account does not exist. Nothing posted. On the balance endpoint: 404. |
| `PREVIOUSLY_REJECTED` | 422 | This posting ID already failed definitively in TigerBeetle; it can never succeed. |
| `POSTING_CONFLICT` | 409 | The posting ID exists with different content (or only some of its legs exist). A caller bug. |
| `ACCOUNT_CONFLICT` | 409 | The account ID exists with different fields. |
| `LEDGER_ERROR` | 500 | Unexpected TigerBeetle result. Logged as an error; alert. |
| `LEDGER_TIMEOUT` | 503 | TigerBeetle did not answer within the deadline, or the client failed; the client was fenced. Outcome unknown (`postingStatus: UNKNOWN`): retry the identical request. `Retry-After: 1`. |
| `OVERLOADED` | 503 | Concurrency limit reached; rejected before touching TigerBeetle. Retry the identical request. `Retry-After: 1`. |

## Configuration

`src/main/resources/application.properties`. Any property can be overridden with an environment variable (Spring relaxed binding: upper case, `.` → `_`, `-` removed).

| Property | Default | Environment variable | Meaning |
| --- | --- | --- | --- |
| `server.port` | `8081` | `SERVER_PORT` | HTTP port |
| `poc.tigerbeetle.cluster-id` | `0` | `POC_TIGERBEETLE_CLUSTERID` | TigerBeetle cluster ID |
| `poc.tigerbeetle.addresses` | `tigerbeetle:3000` | `POC_TIGERBEETLE_ADDRESSES` | Comma-separated replica addresses; hostnames are resolved to IPs at start-up |
| `poc.tigerbeetle.request-deadline` | `800ms` | `POC_TIGERBEETLE_REQUESTDEADLINE` | Deadline of every TigerBeetle request; exceeded → fence + 503 |
| `poc.tigerbeetle.ledger` | `1` | `POC_TIGERBEETLE_LEDGER` | Ledger of every account and transfer (BDT) |
| `poc.posting.max-legs` | `8` | `POC_POSTING_MAXLEGS` | Maximum legs per posting (hard limit 8) |
| `poc.posting.concurrency-limit` | `1024` | `POC_POSTING_CONCURRENCYLIMIT` | Concurrent postings per instance before 503 `OVERLOADED` |
| `poc.accounts.bootstrap` | `true` | `POC_ACCOUNTS_BOOTSTRAP` | Create the fixed system accounts at start-up |
| `poc.accounts.bootstrap-timeout` | `60s` | `POC_ACCOUNTS_BOOTSTRAPTIMEOUT` | How long start-up waits for TigerBeetle before failing |
| `poc.accounts.bootstrap-retry-interval` | `1s` | `POC_ACCOUNTS_BOOTSTRAPRETRYINTERVAL` | Pause between bootstrap attempts |
| `spring.profiles.active` | none | `SPRING_PROFILES_ACTIVE` | `test` enables `POST /internal/v1/fundings`; production omits it |
| `JAVA_OPTS` (container only) | P16 set | `JAVA_OPTS` (compose: `LEDGER_JAVA_OPTS`) | JVM options |

## Observability

- **Probes:** `/actuator/health/liveness` (JVM alive only) and `/actuator/health/readiness` (`readinessState` + `tigerbeetle`, a lookup of the issuance account within the deadline). A TigerBeetle outage makes the instance unready, never restarts it.
- **Metrics** at `/actuator/prometheus`, tagged `application=ledger-service`:
  - `ledger_posting_duration_seconds{outcome}`: the posting use case (histogram).
  - `ledger_tb_request_seconds`: every TigerBeetle request (histogram).
  - `ledger_client_fence_total`: client fences; alert on any increase.
  - plus `http_server_requests_seconds` and JVM metrics.
- **Logs:** JSON (ECS) on stdout, with `postingId` in the MDC for every log line of a posting request. Request and response bodies are never logged.
