# Scala challenge

Hello future colleague! In this assessment, we request that you implement an HTTP application that converts CSV data sets to JSON data sets. The application will provide the following endpoints:
 
## Create Task 
#### POST /task/
Create a task with URI pointing to CSV dataset which will be converted to Json and reuturns taskId.
   - 2 tasks can be run at the same time
   - the task is executed immediately if `running-tasks < 2` 

## List Tasks 
#### GET /task/

## Task Detail 
#### GET /task/[taskId]
Return informations about the task:    
- lines processed
- avg lines processed (count/sec)
- state (`SCHEDULED/RUNNING/DONE/FAILED/CANCELED`)
- result (uri where the JSON file can be downloaded)

Keep the connection open until the task isn't in a terminal state and send the updated response every 2 seconds.

## Cancel Task
#### DELETE /task/[taskId]
Tasks in `SCHEDULED` or `RUNNING` state can be canceled.

## Get JSON File
Endpoint providing generated JSON files.

## Notes
- Keep the state only in memory except generated json files.
- Take into account that input files don't have to fit into memory.

CSV datasets for testing purposes can be found at the following links:
* https://catalog.data.gov/dataset?res_format=CSV
* https://www.kaggle.com/datasets

Feel free to ask any questions through email or github.

Good Luck!

# Implementation

This application is based on `Akka Http` framework and `Akka Streams`. It utilizes `Slick` as ORM and `Guice` for Dependency Injection.
Since `Akka` technologies are used, I decided to stream (almost) everything. All queries that expect to return a list of elements is streamed using the `Alpakka Slick` connector.
Similarly, all the requests that expect to return a list of elements (like the get the JSON file) request, are streamed.
The endpoint that provides info about a task also streams its response until the task is in a terminal state.

The application will always handle the first CSV line as headers.

### Architecture
Its architecture is based on the 3-tier model, mostly for simplicity, as its structure is not very complex.
Each tier is located in a separate module, mostly to demonstrate the loosely coupled layers.
**Notice**: the database and service layer have their own entities, but the controller layer has only where it makes sense.

### Business flow
Regarding the flow responsible for the transformation, I implemented the boundary of 2 concurrent task execution using a `BoundedSourceQueue` that creates an inner stream from the url of the task.
The inner stream is processed in parallel with factor 2.
This is probably not the best approach, but my knowledge of the `Akka streams` framework is limited.
The cancel functionality is achieved using a `KillSwitch` that will abort the inner stream and throw a specific exception that will allow the application to recover knowing that it was canceled and not failed.
Since `Akka streams` make the whole process asynchronous, the cancel request may come before the task has started, so I just mark it as canceled and do not start the flow at all.

### Database Schema

For this task I used an in-memory map for the tasks and a PostgreSQL for the converted JSON data.

* Json data are stored in `json_line` table, each csv-converted line in a record. The actual data is stored in a `text` column.
  Each JSON line has information if it belongs to a completed task (using the `is_complete` boolean column) and the task info.
* A task has an id (randomly generated `UUID`), the number of lines processed, its state and the total processing time. It didn't seem necessary to keep the url.
  When the application starts, it loads in memory all the successfully completed tasks.

### Prerequisites

The project requires 
* [JDK 11](https://www.openlogic.com/openjdk-downloads)
* [Scala 2.13](https://www.scala-lang.org/download/2.13.0.html)
* [sbt 1.5.8](https://www.scala-sbt.org/download.html)
* [docker](https://docs.docker.com/get-docker/)
* [docker-compose](https://docker-docs.netlify.app/compose/install/)

### Usage

#### Enviromental variables
You can configure some parameters using environmental variables. There is the `env.template` file which you can use as reference to pass your own values
You can find the variables and their usage in the table below

| Variable name        | Default value                      | Description      |
|----------------------|------------------------------------|------------------|
| `POSTGRES_URL`       | URL for PostgreSQL instance        | `localhost:5432` |
| `POSTGRES_DB`        | name of database to connect to     | `gwi`            |
| `POSTGRES_USERNAME`  | username of PG user                | `gwi`            |
| `POSTGRES_PASSWORD`  | password of PG user                | `gwi`            | 
| `CONCURRENCY_FACTOR` | number of concurrent tasks running | `2`              | 

#### Compile, run, test
You can use `make` commands to compile, run and test the app:

| Command                 | Description                                                                                                            |
|-------------------------|------------------------------------------------------------------------------------------------------------------------|
| ``make start-services`` | Starts a dockerized PostgreSQL for both testing and dev environment. There is no need to execute any SQL from the user |
| ``make compile``        | Cleans and compiles the project                                                                                        |
| ``make test``           | Starts the dockerized PostgreSQL, compiles the project and runs the tests                                              |
| ``make run``            | Starts the application                                                                                                 |
| ``make docker-buildx``  | Cross builds a docker image of the application and publish it locally                                                  |

#### Endpoints

All endpoints are using the `JSON` format for both requests and responses

| Protocol | Path                    | Description                                                                                                                     | Status code         | Request example                                                                                                                                                                                       | Response example                                                                                                                                                                                       |
|----------|-------------------------|---------------------------------------------------------------------------------------------------------------------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET`    | `ready`                 | Responds if the app is ready to receive requests                                                                                | `200`               |                                                                                                                                                                                                       | `{"isReady": true}`                                                                                                                                                                                    |               
| `POST`   | `/task`                 | Create a new task. It expects a url pointing to the CSV file and returns a UUID that is the task id                             | `202`               | `{"url": "https://www.sample-videos.com/csv/Sample-Spreadsheet-100-rows.csv"}`                                                                                                                        | `{"id": "237dc24b-52b7-4b12-a452-c49a80e5a1b3"}`                                                                                                                                                       |
| `GET`    | `/task`                 | Stream a list of all tasks. If done, a link to fetch the JSON data is included                                                  | `200`               | `[{"id": "237dc24b-52b7-4b12-a452-c49a80e5a1b3", "linesProcessed": 100, "averageLinesProccesed": 2, "state": "Done", "result": "http://localhost:8080/result/237dc24b-52b7-4b12-a452-c49a80e5a1b3"}]` |                                                                                                                                                                                                        |
| `GET`    | `/task/[taskId]`        | Return task details, if the task exists. This will stream the task details every 2 minutes, if the task is not in a final state | `200`, `404`        | `[{"id": "237dc24b-52b7-4b12-a452-c49a80e5a1b3", "linesProcessed": 100, "averageLinesProccesed": 2, "state": "Done", "result": "http://localhost:8080/result/237dc24b-52b7-4b12-a452-c49a80e5a1b3"}]` |                                                                                                                                                                                                        |
| `DELETE` | `task/[taskId]`         | Cancel the task, if it is have not already completed                                                                            | `204`, `404`, `400` |                                                                                                                                                                                                       |                                                                                                                                                                                                        |
| `GET`    | `/task/result/[taskId]` | Stream the JSON lines that were derived from the CSV file                                                                       | `200`, `404`, `400` |                                                                                                                                                                                                       | `[{{"Draw Date":"09/26/2020","Winning Numbers":"11 21 27 36 62 24","Multiplier":"3"}}]`                                                                                                                |

**Error responses**

All error responses return a JSON body with a simple informative message. The format is the following:
`{"message": "informative error message"}`

### Docker

You can cross build a docker image of the application using the `make docker-buildx` command. This will publish locally the built image.
Afterwards, you can run the app with the following command:
`docker run gwi/scala-challenge -p 8080:8080 -e POSTGRES_URL=${POSTGRES_URL} -e POSTGRES_DB=${POSTGRES_DB} -e POSTGRES_USERNAME=${POSTGRES_USERNAME} -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD} -e CONCURRENCY-FACTOR=${CONCURRENCY_FACTOR}`

### Improvements, known issues and challenges occurred

All the process is in memory, although only a piece of the data is loaded and processed each time. This may cause some issues with sources that cannot be backpressured.
A more robust solution could be probably achieved using a message bus (like Apache Kafka), utilizing its persistence, but I decided to create an implementation with as few dependencies as possible.

There are no retries in the flow, so if a step fails, the whole flow will. This was intentional, as a part of the information will be missing.
Some processing can be parallelized, but the transformation of the CSV lines to JSON needs to be serial, as the first line contains the headers.
That is the reason that the division of the lines processed to the total process time gives the correct result for the average lines processed per second.

Regarding the database schema, a `JSONB` column should be more suitable, but `Slick` does not support it directly, but via another library, so I decided at the time being not to dedicate time for this.
Probably a Document DB, like `MongoDB`, or `ElasticSearch` for also supporting text-based search, would be more suitable, but there was no need to add more complexity at the moment.
Although, when requested the lines are returned ordered by time created, we cannot guarantee that they are in the same order as in the CSV file, due to the nature of the transformation, and they shouldn't be, as a JSON array cannot guarantee the correct order as well.

As mentioned all the completed tasks are loaded into memory, from the `json_line` table. I decided to exclude the ones that were not completed, as I cannot deduct correctly their status (failed or canceled),
and again, `Slick` did not directly support some PG capabilities, and more specifically the `bool_and` aggregate function. I could achieve this result in application level, but since it was not a prerequisite, I decided not to implement it.

Database testing was a difficult topic, as slick does not seem to be really easy to use for this task.
More specifically, I tried various implementations for rollbacking transactions after each test, returning to savepoints, etc.
but none seemed to work, as implicit values seem to be the problem.
I decided to go with a simple solution, as the database needs are simple as well and delete all records before and after each test.
I had to do it for both before and each, as there were flows running until the test `ActorSystem` was terminated, inserting records in the database.
This is not an optiomal solution, but it does not cause any issues, as I am using another database within PG for tests.

A really interesting challenge was the metrics of the line processing. For this, after parsing the CSV file to rows, I added the current timestamp.
After that, I created a custom flow that will unzip the tuple, process the CSV line using the `Alpakka CSV` library and then zip the elements again. 
The `Aplakka CSV` library expects the first line to be headers, so it needs to consume 2 elements from the flow to generate a line.
The Zip flow stops the demand until it has 2 elements to merge, thus stopping the flow from the unzip flow and not allowing the CSV process flow to get the second element.
For this I created an additional flow that get the timestamp elements and always drops the first one, allowing the flow to continue.