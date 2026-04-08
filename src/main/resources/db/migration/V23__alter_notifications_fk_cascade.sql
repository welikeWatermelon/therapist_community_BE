ALTER TABLE notifications
    DROP CONSTRAINT fk_notifications_receiver,
    ADD CONSTRAINT fk_notifications_receiver
        FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE notifications
    DROP CONSTRAINT fk_notifications_sender,
    ADD CONSTRAINT fk_notifications_sender
        FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE SET NULL;
