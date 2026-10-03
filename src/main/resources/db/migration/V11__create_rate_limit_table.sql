-- V11: Create rate limits table for Bucket4j PostgreSQL backend

CREATE TABLE rate_limits (
    id VARCHAR(255) PRIMARY KEY,
    state BYTEA
);
