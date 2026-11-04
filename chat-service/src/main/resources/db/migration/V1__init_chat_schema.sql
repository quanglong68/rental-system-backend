-- chat-service schema: conversation 1-1 giua 2 nguoi cho tung ngu canh nghiep vu.
CREATE TABLE conversation (
    id                    BIGSERIAL PRIMARY KEY,
    context_type          VARCHAR(32)  NOT NULL,
    context_id            BIGINT       NOT NULL,
    dedup_key             VARCHAR(160) NOT NULL,
    context_label         VARCHAR(255),
    building_id           BIGINT,
    room_id               BIGINT,
    last_message_id       BIGINT,
    last_message_preview  VARCHAR(300),
    last_message_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_conversation_dedup ON conversation (dedup_key);
CREATE INDEX idx_conversation_last_message ON conversation (last_message_at DESC NULLS LAST);

CREATE TABLE conversation_participant (
    id                     BIGSERIAL PRIMARY KEY,
    conversation_id        BIGINT       NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    user_id                BIGINT       NOT NULL,
    user_type              VARCHAR(32)  NOT NULL,
    role_in_conversation   VARCHAR(32)  NOT NULL,
    display_name           VARCHAR(160) NOT NULL,
    unread_count           INTEGER      NOT NULL DEFAULT 0,
    last_read_message_id   BIGINT,
    last_seen_at           TIMESTAMPTZ,
    joined_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_participant_conversation_user UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_participant_user ON conversation_participant (user_id, conversation_id);
CREATE INDEX idx_participant_conversation ON conversation_participant (conversation_id);

CREATE TABLE chat_message (
    id                 BIGSERIAL PRIMARY KEY,
    conversation_id    BIGINT       NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    sender_id          BIGINT       NOT NULL,
    sender_type        VARCHAR(32)  NOT NULL,
    sender_name        VARCHAR(160) NOT NULL,
    client_message_id  VARCHAR(64),
    content            VARCHAR(2000) NOT NULL,
    message_type       VARCHAR(16)  NOT NULL DEFAULT 'TEXT',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_message_conversation ON chat_message (conversation_id, id DESC);
CREATE UNIQUE INDEX uq_message_client ON chat_message (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
