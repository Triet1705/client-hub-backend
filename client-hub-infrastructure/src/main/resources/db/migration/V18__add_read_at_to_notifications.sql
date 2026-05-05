ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_notif_read_at ON notifications(is_read, read_at);
