# Event Publication Operations

Spring Modulith durable publications are stored in `event_publication`. Prometheus exposes:

- `modulith_event_publications_incomplete`
- `modulith_event_publication_oldest_age_seconds`
- `modulith_event_publications_retry_pending`

Use the following read-only query before deployment, after deployment, and during a listener incident:

```sql
SELECT listener_id,
       count(*) AS incomplete_count,
       min(publication_date) AS oldest_publication,
       max(completion_attempts) AS max_attempts
FROM event_publication
WHERE completion_date IS NULL
GROUP BY listener_id
ORDER BY oldest_publication;
```

An incomplete row with `completion_attempts > 0` has failed at least one delivery attempt. Keep the consumer enabled until the backlog reaches zero; do not delete rows to acknowledge an event.

Outstanding publications are resubmitted when the application restarts. After a listener fix, restart one backend instance, watch the three metrics above, and confirm the backlog reaches zero before restarting additional instances.

Completed rows are audit history rather than backlog. Retain them for at least 30 days, then clean only completed rows during a maintenance window:

```sql
DELETE FROM event_publication
WHERE completion_date IS NOT NULL
  AND completion_date < current_timestamp - interval '30 days';
```

Never delete rows whose `completion_date` is null. Preserve them for retry and incident analysis.
