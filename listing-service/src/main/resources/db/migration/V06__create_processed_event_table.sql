create table processed_event
(
    event_id     uuid                                               not null
        primary key,
    processed_at timestamp with time zone                           not null,
    created_at   timestamp with time zone default CURRENT_TIMESTAMP not null,
    updated_at   timestamp with time zone default CURRENT_TIMESTAMP not null
);

