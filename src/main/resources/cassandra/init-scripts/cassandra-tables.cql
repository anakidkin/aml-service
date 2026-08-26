CREATE KEYSPACE IF NOT EXISTS aml_audit
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS aml_audit.audit_logs
(
    transaction_id    uuid,
    created_at        timestamp,
    rule_id           text,
    rule_version      int,
    status            text,
    trigger_reason    text,
    execution_time_ms bigint,
    is_hard           boolean,
    risk_score         double,
    PRIMARY KEY ((transaction_id), created_at, rule_id)
) WITH CLUSTERING ORDER BY (created_at DESC, rule_id ASC);

CREATE KEYSPACE IF NOT EXISTS aml_ks
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS aml_ks.account_transaction_history
(
    account_from   text,
    is_p2p         boolean,
    created_at     timestamp,
    transaction_id uuid,
    account_to     text,
    amount         decimal,
    currency       text,
    mcc_code       text,
    PRIMARY KEY ((account_from), is_p2p, created_at, transaction_id)
) WITH CLUSTERING ORDER BY (is_p2p ASC, created_at DESC, transaction_id ASC);

CREATE TABLE IF NOT EXISTS aml_ks.account_counterparties
(
    account_to   text,
    time_window  text, -- YYYY-MM-DD
    account_from text,
    created_at   timestamp,
    PRIMARY KEY ((account_to, time_window), account_from)
);