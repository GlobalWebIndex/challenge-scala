CREATE DATABASE tasks_db;
\c tasks_db;

CREATE TABLE tasks (
  id BIGSERIAL PRIMARY KEY,
  uri VARCHAR,
  status VARCHAR
);