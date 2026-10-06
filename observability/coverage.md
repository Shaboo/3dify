# Observability coverage

Instrumentation is enabled with the application. Prometheus metrics retain the existing `application` and `environment` labels. `APP_LOG_LEVEL` defaults to `INFO`; set it to `DEBUG` temporarily for dependency completions, normal polling, and expected rejections. No request bodies, tokens, email addresses, customer webhook URLs, raw request paths, query strings, method arguments, results, or SDK exception messages are logged by the new instrumentation.

## Coverage

| Boundary | Signals |
|---|---|
| HTTP, including authentication failures | Existing `http.server.requests` latency/count/status metrics; `http_request` completion logs with route templates, status, duration and `requestId` |
| Every application service `execute` | `omni3d.operations.duration` timer with `layer=application`, class/method `operation`, and `outcome=success/rejected/error`; application latency histograms and completion logs |
| Repositories and transaction provider | The same operation timers with `layer=dependency`, including DB failures; Hikari metrics track pool pressure |
| Meshy, RunPod, Shopify, Stripe and customer callbacks | Dependency timers and failure logs; existing billing, generation, storage and delivery business counters |
| Storage, asset downloads, auth verifiers, password hashing and rate limiting | Dependency timers and failure logs; existing upload and authorization counters plus `omni3d.jwt.validations` with valid/invalid outcomes |
| Rabbit listener and scheduled reconciliation/privacy jobs | Operation timers with `layer=background`; malformed listener payloads and uncaught scheduler errors count as errors |
| Outbox poll, relay and delivery | Dependency operation timers; existing per-message published/failed counters |
| Generation dispatch/reconciliation, callbacks, Shopify reconciliation/privacy/webhooks | `omni3d.workflow.items` counts explicit per-item results, including caught errors, pending tasks, skipped work, scheduled retries and uncertain submissions |
| Persisted unfinished work | `omni3d.backlog.items` and `omni3d.backlog.oldest.age` gauges for outbox, privacy, and each active generation state |
| In-progress calls | `omni3d.operations.active` long-task timers expose concurrent calls and active duration |
| JVM and process | Existing Actuator memory, threads, GC, CPU and process metrics; collector no longer drops process and GC metrics |

Operation `success` means a method returned normally. Use business and workflow counters to determine whether an individual job succeeded. Boundary durations are inclusive: a service includes the time spent in its dependencies. Do not sum timers from different layers. Overloaded methods share their class/method label. Spring proxies observe public calls through Spring beans; private methods, self-invocation, objects created with constructors, and streamed data consumed after a method returns are outside these timers. New application services are covered automatically. New adapters should use the existing Repository/Client/Verifier/Storage naming or be added to the operation pointcut.

Backlogs are sampled with three aggregate SQL queries every 30 seconds, independently of worker enablement. Configure `observability.backlog-delay-ms` to change the interval. Gauges are `NaN` until a first successful sample. A failed sample preserves the last snapshot, increments `omni3d.backlog.sample.failures`, and leaves `omni3d.backlog.last.success` unchanged, so stale data can be alerted on. Database scans add read load proportional to unfinished work. `generation_uncertain` indicates an upstream submission that requires investigation before any manual resubmission.

## Correlation

HTTP responses carry `X-Request-ID`. Safe incoming values (1–64 ASCII letters, numbers, underscores or hyphens) are preserved; other values are replaced with UUIDs. Request context is restored after completion or failure. Rabbit workers establish and restore job context; use a job ID to follow a generation across HTTP submission, asynchronous dispatch and callbacks. Request IDs are not propagated in the existing Rabbit wire contract. Actuator requests are omitted from access logs to avoid scrape noise. For requests blocked before routing, the logged route is `unmatched`.

Keep IDs in log content, never in Prometheus or Loki labels:

```logql
{job="omni3d-backend"} |= "requestId=<request-id>"
{job="omni3d-backend"} |= "jobId=<job-id>"
{job="omni3d-backend"} |= "operation_failed"
```

The existing Promtail parser accepts the expanded named MDC context. The example collector still requires a real log-file mount: the application's default logging destination is stdout. Configure a mounted file using `LOGGING_FILE_NAME` and `LOGGING_PATTERN_FILE` matching the console pattern, or collect container stdout with your deployed log collector.

## Dashboard queries

Calls per second and failures, grouped by boundary:

```promql
sum by (layer, operation, outcome) (rate(omni3d_operations_duration_seconds_count[5m]))
sum by (operation) (rate(omni3d_operations_duration_seconds_count{outcome="error"}[5m]))
```

Application latency p95 in seconds:

```promql
histogram_quantile(0.95, sum by (operation, le) (rate(omni3d_operations_duration_seconds_bucket{layer="application"}[5m])))
```

Mean dependency latency in seconds:

```promql
sum by (operation) (rate(omni3d_operations_duration_seconds_sum{layer="dependency"}[5m]))
/
sum by (operation) (rate(omni3d_operations_duration_seconds_count{layer="dependency"}[5m]))
```

Per-item background failures and backlog age:

```promql
sum by (workflow, outcome) (increase(omni3d_workflow_items_total[15m]))
max by (queue) (omni3d_backlog_oldest_age_seconds)
time() - omni3d_backlog_last_success_seconds
```

Add `environment`/`application` filters for your deployment. Empty counters are created on first observation; missing series before the first event is normal.

## Collection and alerting

`/actuator/prometheus` retains its existing authentication requirement. Supply a valid bearer credential to the collector and rotate it before expiry; the example scraper requires deployment-specific authentication configuration. This change does not publish an unauthenticated endpoint. Check the scrape target's `up` metric before relying on dashboards.

[alerts.yml](alerts.yml) contains starting thresholds for a Prometheus server or compatible ruler. Import it into your alerting system and adjust the thresholds to your traffic and generation SLA. Prometheus Agent mode only scrapes and remote-writes; it does not evaluate these rules. No dashboards, collector processes, or alert rules are deployed by this code change.
