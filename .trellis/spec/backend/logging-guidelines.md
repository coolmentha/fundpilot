# Logging Guidelines

> How logging is done in this project.

---

## Overview

<!--
Document your project's logging conventions here.

Questions to answer:
- What logging library do you use?
- What are the log levels and when to use each?
- What should be logged?
- What should NOT be logged (PII, secrets)?
-->

(To be filled by the team)

---

## Log Levels

<!-- When to use each level: debug, info, warn, error -->

(To be filled by the team)

---

## Structured Logging

<!-- Log format, required fields -->

(To be filled by the team)

---

## What to Log

### Exception context

Caught exceptions must be passed to SLF4J as the final argument so logs retain the
exception type, stack trace, and cause chain. Keep identifiers and fallback actions
in the message template, but do not log complete external payloads or credentials.

```java
// Wrong: drops the stack trace and nested cause.
log.warn("基金 {} 估值刷新失败: {}", fundCode, exception.getMessage());

// Correct: keeps business context and the complete exception.
log.warn("基金 {} 估值刷新失败", fundCode, exception);
```

---

## What NOT to Log

<!-- Sensitive data, PII, secrets -->

(To be filled by the team)
