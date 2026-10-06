# Omni3D — Grafana Dashboard Guide

After signing up for Grafana Cloud and deploying with the new observability sidecars, follow this guide to build your monitoring dashboard.

---

## Step 1: Add Data Sources

In Grafana Cloud, both data sources come pre-configured. Confirm them under **Connections > Data Sources**:

| Data Source | Type | Purpose |
|---|---|---|
| `grafanacloud-<name>-prom` | Prometheus | Metrics from `/actuator/prometheus` |
| `grafanacloud-<name>-logs` | Loki | Application logs shipped by Promtail |

---

## Step 2: Create a Dashboard

**Dashboards > New > New Dashboard**

Create panels using the sections below. Set the time range to **Last 1 hour** as default. Add a variable `$env` to filter by environment:

- **Variable name**: `env`
- **Type**: Query
- **Query**: `label_values(omni3d_jobs_created_total, environment)`

---

## Dashboard Layout (5 rows)

---

### Row 1: Business KPIs

#### Panel: Jobs Submitted (Rate)
> How many jobs per minute across all time?

**Type**: Time series  
**PromQL:**
```promql
rate(omni3d_jobs_created_total{environment="$env"}[5m]) * 60
```

#### Panel: Job Success Rate
> What % of jobs complete successfully?

**Type**: Gauge (0–100%)  
**PromQL:**
```promql
100 * (
  rate(omni3d_jobs_completed_total{result="success", environment="$env"}[1h])
  /
  (
    rate(omni3d_jobs_completed_total{result="success", environment="$env"}[1h]) +
    rate(omni3d_jobs_completed_total{result="failed", environment="$env"}[1h])
  )
)
```

#### Panel: Job Failure Rate
> How many jobs per minute are failing?

**Type**: Stat  
**PromQL:**
```promql
rate(omni3d_jobs_completed_total{result="failed", environment="$env"}[5m]) * 60
```
**Thresholds**: 0 = green, 0.1 = yellow, 0.5 = red

#### Panel: Job Duration — p50 / p95 / p99
> How long does a typical job take end-to-end?

**Type**: Time series  
**PromQL (three queries):**
```promql
# p50
histogram_quantile(0.50, sum(rate(omni3d_jobs_duration_seconds_bucket{environment="$env"}[5m])) by (le))

# p95
histogram_quantile(0.95, sum(rate(omni3d_jobs_duration_seconds_bucket{environment="$env"}[5m])) by (le))

# p99
histogram_quantile(0.99, sum(rate(omni3d_jobs_duration_seconds_bucket{environment="$env"}[5m])) by (le))
```

#### Panel: New Users Registered
**Type**: Stat  
**PromQL:**
```promql
increase(omni3d_users_registered_total{environment="$env"}[$__range])
```

#### Panel: Active Subscriptions Ratio
**Type**: Stat  
**PromQL:**
```promql
# Subscriptions activated vs canceled (net activations in range)
increase(omni3d_subscriptions_activated_total{environment="$env"}[$__range]) -
increase(omni3d_subscriptions_canceled_total{environment="$env"}[$__range])
```

---

### Row 2: API Health

#### Panel: HTTP Request Rate by Endpoint
**Type**: Time series  
**PromQL:**
```promql
sum by (uri) (
  rate(http_server_requests_seconds_count{application="omni3d-api", environment="$env"}[5m])
)
```

#### Panel: HTTP Error Rate (4xx + 5xx)
**Type**: Time series  
**PromQL:**
```promql
# 5xx errors
sum(rate(http_server_requests_seconds_count{
  application="omni3d-api",
  environment="$env",
  status=~"5.."
}[5m])) * 60

# 4xx errors
sum(rate(http_server_requests_seconds_count{
  application="omni3d-api",
  environment="$env",
  status=~"4.."
}[5m])) * 60
```

#### Panel: HTTP Latency p95 by URI
**Type**: Heatmap or Table  
**PromQL:**
```promql
histogram_quantile(0.95,
  sum by (uri, le) (
    rate(http_server_requests_seconds_bucket{
      application="omni3d-api",
      environment="$env"
    }[5m])
  )
)
```

#### Panel: API Auth Failures by Reason
**Type**: Time series (stacked)  
**PromQL:**
```promql
sum by (reason) (
  rate(omni3d_auth_failures_total{environment="$env"}[5m])
) * 60
```
> Shows `invalid_key`, `no_subscription`, `rate_limit` as separate series.

#### Panel: Rate Limit Rejections / min
**Type**: Stat  
**PromQL:**
```promql
rate(omni3d_rate_limit_rejected_total{environment="$env"}[5m]) * 60
```

#### Panel: API Key Validation Result
**Type**: Pie chart  
**PromQL:**
```promql
sum by (result) (
  rate(omni3d_api_keys_validations_total{environment="$env"}[1h])
)
```

---

### Row 3: Job Pipeline

#### Panel: Outbox Publish Rate
**Type**: Time series  
**PromQL:**
```promql
rate(omni3d_outbox_published_total{environment="$env"}[5m]) * 60
```

#### Panel: Outbox Failures
**Type**: Stat  
**PromQL:**
```promql
increase(omni3d_outbox_failed_total{environment="$env"}[$__range])
```
**Thresholds**: 0 = green, 1 = red

#### Panel: RunPod Dispatch Rate
**Type**: Time series  
**PromQL:**
```promql
rate(omni3d_runpod_dispatched_total{environment="$env"}[5m]) * 60
```

#### Panel: RunPod Callback Results
**Type**: Pie chart  
**PromQL:**
```promql
sum by (result) (
  rate(omni3d_runpod_callbacks_total{environment="$env"}[1h])
)
```

#### Panel: User Webhook Delivery Success Rate
**Type**: Gauge  
**PromQL:**
```promql
100 * (
  rate(omni3d_webhooks_deliveries_total{result="success", environment="$env"}[1h]) /
  rate(omni3d_webhooks_deliveries_total{environment="$env"}[1h])
)
```

---

### Row 4: JVM & Infrastructure

#### Panel: JVM Heap Usage
**Type**: Time series  
**PromQL:**
```promql
jvm_memory_used_bytes{area="heap", application="omni3d-api"}
/
jvm_memory_max_bytes{area="heap", application="omni3d-api"}
* 100
```

#### Panel: CPU Usage
**Type**: Time series  
**PromQL:**
```promql
process_cpu_usage{application="omni3d-api"} * 100
```

#### Panel: Active DB Connections
**Type**: Time series  
**PromQL:**
```promql
hikaricp_connections_active{application="omni3d-api"}
```

#### Panel: DB Connection Pool Pending
**Type**: Stat  
**PromQL:**
```promql
hikaricp_connections_pending{application="omni3d-api"}
```
**Thresholds**: 0 = green, 1 = yellow, 5 = red

#### Panel: GC Pause Time
**Type**: Time series  
**PromQL:**
```promql
rate(jvm_gc_pause_seconds_sum{application="omni3d-api"}[5m])
```

#### Panel: RabbitMQ Message Rate
**Type**: Time series  
**PromQL:**
```promql
rate(spring_rabbitmq_listener_seconds_count{application="omni3d-api"}[5m])
```

---

### Row 5: Log Explorer

#### Panel: Log Volume by Level
**Type**: Time series (bar chart)  
**LogQL:**
```logql
sum by (level) (
  count_over_time({job="omni3d-backend"}[1m])
)
```

#### Panel: Error Logs (Live)
**Type**: Logs panel  
**LogQL:**
```logql
{job="omni3d-backend", level="ERROR"}
```

#### Panel: Logs for a Specific Job ID
**Type**: Logs panel  
**LogQL:**
```logql
{job="omni3d-backend"} |= "jobId=<paste-uuid-here>"
```
> Every log line emitted while that job was processing — from creation to GPU dispatch to completion.

#### Panel: Authentication Failure Logs
**Type**: Logs panel  
**LogQL:**
```logql
{job="omni3d-backend"} |= "http_request" |= "status=401"
```

#### Panel: Stripe Webhook Events
**Type**: Logs panel  
**LogQL:**
```logql
{job="omni3d-backend"} |= "Stripe event received"
```

#### Panel: Job Failure Messages
**Type**: Logs panel  
**LogQL:**
```logql
{job="omni3d-backend"} |~ "Generation dispatch failed|Generation completed.*FAILED|Generation reconciled.*FAILED"
```

---

## Step 3: Set Up Alerts

**Alerting > Alert Rules > New Alert Rule**

| Alert | Condition | Severity |
|---|---|---|
| High job failure rate | `rate(omni3d_jobs_completed_total{result="failed"}[5m]) > 0.1` | Warning |
| Job failure rate critical | `rate(omni3d_jobs_completed_total{result="failed"}[5m]) > 0.5` | Critical |
| Outbox failures detected | `increase(omni3d_outbox_failed_total[15m]) > 0` | Warning |
| RunPod dispatch errors | `increase(omni3d_runpod_errors_total[15m]) > 3` | Warning |
| API 5xx spike | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 1` | Critical |
| JVM heap > 85% | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85` | Warning |
| DB connections pending | `hikaricp_connections_pending > 3` | Warning |
| Rate limit rejections spike | `rate(omni3d_rate_limit_rejected_total[5m]) > 5` | Info |

Set the notification channel in **Alerting > Contact Points**.

---

## Step 4: Import JVM Dashboard (Free)

1. **Dashboards > New > Import**
2. Enter ID: **`4701`** (JVM Micrometer — official Spring Boot dashboard)
3. Select your Prometheus data source

Gives you full JVM, GC, thread pool, and HikariCP panels instantly.

---

## Tips

- **Correlate logs + metrics**: Put a metric panel and a log panel side by side in Grafana. When you see a failure spike in the chart, filter logs by `jobId` to get the full trace.
- **MDC jobId**: Request and worker logs can be correlated with request IDs and job IDs in log content, so you can query `{job="omni3d-backend"} |= "jobId=<uuid>"` in Loki to trace a single job end-to-end.
- **Tempo (optional)**: Grafana Cloud's free tier includes Tempo for distributed tracing. Add `micrometer-tracing-bridge-brave` later for trace-to-log correlation with zero extra infrastructure.

See [coverage and queries](coverage.md) for operation, background workflow, backlog and request correlation metrics.
