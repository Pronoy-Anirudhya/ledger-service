# ledger-service — progress

Definition of DONE (from the build brief). Work loop: pick the next unchecked item, implement it, run `./gradlew clean build`, tick it.

## Definition of DONE
- [x] Every feature in brief section 2 is implemented as specified
  - [x] 1. Endpoints per spec 7.2 (accounts, postings, posting lookup, balance, fundings [test profile], health liveness/readiness, prometheus)
  - [x] 2. Chart-of-accounts bootstrap at startup (idempotent)
  - [x] 3. Atomic posting (one TransferBatch, deterministic IDs, LINKED, user_data, validation)
  - [x] 4. Result mapping per spec 8.2 (adapted to TigerBeetle 0.17 per-event results, D7–D11, D39)
  - [x] 5. FencedClientHolder, async calls + 800 ms deadline, fencing on timeout → 503 LEDGER_TIMEOUT
  - [x] 6. Backpressure: @ConcurrencyLimit (REJECT) → 503 + Retry-After: 1
  - [x] 7. RFC 9457 ProblemDetail with machine-readable `code` for all errors
  - [x] 8. openapi/ledger-api.yaml (OpenAPI 3.1), served at GET /openapi.yaml
  - [x] 9. Dockerfile + docker-compose.yml (tigerbeetle-format, tigerbeetle, ledger-service) + README run steps
- [x] `./gradlew clean build` succeeds with zero compile errors and all unit/slice tests passing
- [x] Integration test against a real TigerBeetle (Testcontainers): 4-leg posting, insufficient funds (nothing posted), replay → POSTED. **Not skipped**: it ran against Docker in the final build.
- [x] openapi/ledger-api.yaml is valid OpenAPI 3.1 and matches the implemented endpoints
- [x] `docker compose config` is valid; Dockerfile builds
- [x] README explains build, run, and how transaction-service connects (base URL, fixed system account IDs)

## Notes
- Final `./gradlew clean build` (2026-10-08): 255 tests, 0 failures, 0 skipped. That includes `TigerBeetleLedgerIT` (5 tests against `ghcr.io/tigerbeetle/tigerbeetle:0.17.9`).
- Contract checks:
  - `OpenApiSpecTest` parses the YAML as 3.1 with no messages, and compares its schemas to the Java records and enums.
  - `OpenApiContractTest` checks that the handler mappings and the YAML `/internal/**` operations are the same set.
- Compose smoke test (`docker compose --profile init run --rm tigerbeetle-format`, then `docker compose up -d --build`):
  - The container is healthy.
  - Every README curl example returns the documented response (account created/exists, funding, 4-leg posting, replay, lookup, balances, 422 / 400 / 404 problems).
  - Prometheus exposes the ledger metrics.
- Live fault check F4 (TigerBeetle paused with `docker pause`):
  - The posting gets 503 `LEDGER_TIMEOUT` (`postingStatus: UNKNOWN`, `Retry-After: 1`), readiness goes DOWN, and the client is fenced.
  - After unpausing, the identical retry gets 200 POSTED `replay:true`, and readiness is UP again.
- Found and fixed in the smoke test:
  - The service container needs `seccomp=unconfined`, because the TigerBeetle Java client also uses io_uring (D40).
  - `/openapi.yaml` was served as octet-stream (D41).
