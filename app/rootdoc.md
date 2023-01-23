Running the compiled program starts a web server that maintains a number of
tasks, each of which fetches some CSV by a provided URL, converts it to
JSON, and saves it to a local file.
Endpoints:
  - `GET /check`: simply indicates the system is running.
  - `POST /task`: creates a new task and returns its identifier in plain
    text format; URL should be provided in the request body, as in
    {{{curl 'http://host:port/task' -d 'http://example.com/data.csv'}}}
  - `GET /task`: lists abbreviated information about the existing tasks in a
    JSON format. This includes the link to the result file, if the task
    finished correctly.
  - `GET /task/\$taskId`: gets somewhat more detailed information about the
    given task; updates every two seconds (in a default configuration).
  - `DELETE /task/\$taskId`: cancels the task; does nothing if the task
    already finished.
All configuration can be done with `application.conf`. Most important
parameters are:
  - `csvToJson.host` and `csvToJson.port`: host and port for the server.
  - `csvToJson.resultDirectory`: the directory where the result should be
    stored; this directory should already exist and be writable.
  - `csvToJson.pollingPeriodMillis`: the approximate period between updates
    for the `GET /task/\$taskId` endpoint.
  - `pool.concurrency`: how many tasks should be running simultaneously. The
    value of 0 means there is no limit.
