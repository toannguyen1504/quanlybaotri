CREATE TABLE equipment_documents (
    id UUID PRIMARY KEY,
    equipment_id UUID NOT NULL REFERENCES equipment(id),
    document_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    uploaded_by_id UUID NOT NULL,
    uploaded_by_name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by_id UUID
);
CREATE INDEX idx_equipment_documents_active ON equipment_documents(equipment_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_equipment_documents_delete_pending ON equipment_documents(deleted_at) WHERE deleted_at IS NOT NULL;
