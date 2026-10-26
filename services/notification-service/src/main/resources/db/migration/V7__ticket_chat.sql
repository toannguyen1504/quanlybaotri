CREATE TABLE chat_conversations (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL UNIQUE,
    ticket_code VARCHAR(30) NOT NULL,
    ticket_title VARCHAR(200) NOT NULL,
    ticket_status VARCHAR(30) NOT NULL,
    requester_id UUID NOT NULL,
    assignee_id UUID,
    last_message_sequence BIGINT,
    last_message_preview VARCHAR(240),
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_chat_conversations_requester ON chat_conversations(requester_id, last_message_at DESC);
CREATE INDEX idx_chat_conversations_assignee ON chat_conversations(assignee_id, last_message_at DESC);
CREATE INDEX idx_chat_conversations_activity ON chat_conversations(last_message_at DESC);

CREATE SEQUENCE chat_message_sequence START WITH 1 INCREMENT BY 1;
CREATE TABLE chat_messages (
    sequence_no BIGINT PRIMARY KEY DEFAULT nextval('chat_message_sequence'),
    id UUID NOT NULL UNIQUE,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    client_message_id UUID NOT NULL,
    content VARCHAR(5000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_chat_message_sender_client UNIQUE(sender_id, client_message_id)
);
CREATE INDEX idx_chat_messages_conversation_sequence
    ON chat_messages(conversation_id, sequence_no DESC);

CREATE TABLE chat_read_states (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    last_read_sequence BIGINT NOT NULL DEFAULT 0,
    read_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_chat_read_conversation_user UNIQUE(conversation_id, user_id)
);
CREATE INDEX idx_chat_read_states_user ON chat_read_states(user_id, conversation_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000)
);
CREATE INDEX idx_notification_outbox_unpublished
    ON outbox_events(occurred_at) WHERE published_at IS NULL;
