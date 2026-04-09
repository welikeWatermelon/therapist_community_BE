CREATE TABLE notifications (
    id                BIGSERIAL    PRIMARY KEY,
    receiver_id       BIGINT       NOT NULL,
    sender_id         BIGINT,
    notification_type VARCHAR(50)  NOT NULL,
    reference_id      BIGINT,
    content           VARCHAR(500) NOT NULL,
    is_read           BOOLEAN      NOT NULL DEFAULT false,
    read_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,

    CONSTRAINT fk_notifications_receiver
        FOREIGN KEY (receiver_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_sender
        FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_notifications_receiver_created
    ON notifications (receiver_id, created_at DESC);

CREATE INDEX idx_notifications_receiver_unread
    ON notifications (receiver_id, is_read)
    WHERE is_read = false;