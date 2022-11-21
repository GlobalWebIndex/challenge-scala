CREATE DATABASE tasks_db;
\c tasks_db;

CREATE TABLE tasks (
  id BIGSERIAL PRIMARY KEY,
  uri VARCHAR,
  status VARCHAR,
  time_started BIGINT,
  time_ended BIGINT
);

CREATE TABLE task_results (
  id BIGSERIAL PRIMARY KEY,
  csv_content VARCHAR NOT NULL,
  task_id BIGINT NOT NULL REFERENCES tasks(id)
);

CREATE INDEX task_results_task_id_idx ON task_results(task_id);