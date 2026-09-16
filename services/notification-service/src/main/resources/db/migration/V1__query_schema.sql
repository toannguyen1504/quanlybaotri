CREATE TABLE departments (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL,
    description VARCHAR(500), location VARCHAR(255), contact_email VARCHAR(190), contact_phone VARCHAR(30),
    active BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE roles (id UUID PRIMARY KEY, name VARCHAR(30) NOT NULL UNIQUE, description VARCHAR(255));
INSERT INTO roles(id,name,description) VALUES
('00000000-0000-0000-0000-000000000001','REQUESTER','Projection'),
('00000000-0000-0000-0000-000000000002','TECHNICIAN','Projection'),
('00000000-0000-0000-0000-000000000003','MANAGER','Projection'),
('00000000-0000-0000-0000-000000000004','ADMIN','Projection');
CREATE TABLE users (
    id UUID PRIMARY KEY, username VARCHAR(80) NOT NULL UNIQUE, email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, full_name VARCHAR(150) NOT NULL, phone VARCHAR(30), department_id UUID REFERENCES departments(id),
    enabled BOOLEAN NOT NULL, must_change_password BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE user_roles(user_id UUID NOT NULL REFERENCES users(id),role_id UUID NOT NULL REFERENCES roles(id),PRIMARY KEY(user_id,role_id));
CREATE TABLE notifications (
    id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),event_id UUID NOT NULL,type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,message VARCHAR(1000) NOT NULL,reference_type VARCHAR(50),reference_id UUID,
    read_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL,CONSTRAINT uk_notification_event_user UNIQUE(event_id,user_id)
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id,read_at,created_at);
CREATE TABLE processed_messages(event_id UUID PRIMARY KEY,processed_at TIMESTAMPTZ NOT NULL);
CREATE TABLE ticket_dashboard (
    ticket_id UUID PRIMARY KEY,ticket_code VARCHAR(30) NOT NULL,status VARCHAR(30) NOT NULL,priority VARCHAR(20) NOT NULL,
    assignee_name VARCHAR(150),category_name VARCHAR(150),submitted_at TIMESTAMPTZ NOT NULL,
    resolution_due_at TIMESTAMPTZ NOT NULL,resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_ticket_dashboard_period ON ticket_dashboard(submitted_at);
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,actor_id UUID,action VARCHAR(100) NOT NULL,entity_type VARCHAR(100),entity_id VARCHAR(100),
    method VARCHAR(10) NOT NULL,path VARCHAR(500) NOT NULL,status INTEGER NOT NULL,created_at TIMESTAMPTZ NOT NULL
);
