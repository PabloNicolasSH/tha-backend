ALTER TABLE app_user
    ADD COLUMN branch_id BIGINT NULL,
    ADD CONSTRAINT fk_app_user_branch FOREIGN KEY (branch_id) REFERENCES branch (id);
