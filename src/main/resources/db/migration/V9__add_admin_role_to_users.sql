-- V9: Add admin role flag to users table

ALTER TABLE users
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;
