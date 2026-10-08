# ledger-service
Product-agnostic ledger service for an MFS Send Money POC. Java 25 + Spring Boot 4.1.1 gateway to TigerBeetle that posts multi-leg transfers (principal, fee, VAT, commission) as one atomic linked batch, with deterministic idempotent IDs, fenced client on timeout, and auto-batching for high throughput.
