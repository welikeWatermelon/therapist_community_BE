ALTER TABLE admin_logs
    ADD COLUMN library_resource_id BIGINT REFERENCES library_resources(id),
    ADD COLUMN library_resource_report_id BIGINT REFERENCES library_resource_reports(id);

CREATE  INDEX  idx_admin_logs_library_resource_id
    ON admin_logs (library_resource_id);

CREATE  INDEX  idx_admin_logs_library_resource_report_id
    ON admin_logs (llibrary_resource_report_id);