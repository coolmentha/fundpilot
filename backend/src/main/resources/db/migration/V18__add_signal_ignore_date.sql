ALTER TABLE signal_log
    ADD COLUMN ignored_date TIMESTAMPTZ;

CREATE INDEX idx_signal_log_action_queue
    ON signal_log (signal_date DESC)
    WHERE deleted_date IS NULL
      AND ignored_date IS NULL
      AND signal_type <> 'NONE';
