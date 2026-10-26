CREATE INDEX IF NOT EXISTS idx_chat_conversations_status_activity
    ON chat_conversations(ticket_status, last_message_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_chat_conversations_code_lower
    ON chat_conversations(lower(ticket_code));
CREATE INDEX IF NOT EXISTS idx_chat_messages_created
    ON chat_messages(created_at DESC);
