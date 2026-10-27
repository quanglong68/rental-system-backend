create table outbox_event
(
    outbox_event_id uuid                                               not null
        primary key,
    topic           varchar(150)                                       not null,
    event_key       varchar(100)                                       not null,
    event_type      varchar(100)                                       not null,
    payload         jsonb                                              not null,
    created_at      timestamp with time zone default CURRENT_TIMESTAMP not null,
    updated_at      timestamp with time zone default CURRENT_TIMESTAMP not null,
    published_at    timestamp with time zone,
    claimed_by      uuid,
    claimed_until   timestamp with time zone,
    attempt_count   integer                  default 0                 not null,
    next_attempt_at timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index idx_outbox_event_unpublished
    on outbox_event (created_at, outbox_event_id) where (published_at IS NULL);

create index idx_outbox_event_claimable
    on outbox_event (next_attempt_at, created_at) where (published_at IS NULL);

