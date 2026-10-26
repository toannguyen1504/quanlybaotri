ALTER TABLE tickets
    ADD COLUMN feedback_rating SMALLINT,
    ADD COLUMN feedback_comment TEXT,
    ADD COLUMN feedback_submitted_at TIMESTAMPTZ;

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_feedback_rating
        CHECK (feedback_rating IS NULL OR feedback_rating BETWEEN 1 AND 5),
    ADD CONSTRAINT chk_ticket_feedback_comment
        CHECK (
            feedback_comment IS NULL OR
            (char_length(btrim(feedback_comment)) BETWEEN 1 AND 5000)
        ),
    ADD CONSTRAINT chk_ticket_feedback_consistency
        CHECK (
            (
                feedback_rating IS NULL AND
                feedback_comment IS NULL AND
                feedback_submitted_at IS NULL
            ) OR (
                feedback_submitted_at IS NOT NULL AND
                (feedback_rating IS NOT NULL OR feedback_comment IS NOT NULL)
            )
        );
