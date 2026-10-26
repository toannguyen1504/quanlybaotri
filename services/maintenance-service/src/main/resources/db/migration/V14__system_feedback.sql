CREATE TABLE system_feedback (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    response TEXT,
    reviewed_by_id UUID,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_system_feedback_category CHECK (
        category IN ('BUG_REPORT', 'SUGGESTION', 'FEATURE_REQUEST')
    ),
    CONSTRAINT chk_system_feedback_status CHECK (
        status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')
    ),
    CONSTRAINT chk_system_feedback_title CHECK (
        char_length(btrim(title)) BETWEEN 1 AND 200
    ),
    CONSTRAINT chk_system_feedback_content CHECK (
        char_length(btrim(content)) BETWEEN 1 AND 5000
    ),
    CONSTRAINT chk_system_feedback_response CHECK (
        response IS NULL OR char_length(btrim(response)) BETWEEN 1 AND 5000
    ),
    CONSTRAINT chk_system_feedback_terminal_response CHECK (
        status NOT IN ('RESOLVED', 'REJECTED') OR response IS NOT NULL
    )
);

CREATE INDEX idx_system_feedback_requester_created
    ON system_feedback(requester_id, created_at DESC);
CREATE INDEX idx_system_feedback_status_created
    ON system_feedback(status, created_at DESC);
CREATE INDEX idx_system_feedback_category_created
    ON system_feedback(category, created_at DESC);
