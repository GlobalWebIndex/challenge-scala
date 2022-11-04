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

### Architecture
Its architecture is based on the 3-tier model, mostly for simplicity, as its structure is not very complex.
Each tier is located in a separate module, mostly to demonstrate the loose coupling of the layers.

### Business flow
Regarding the flow responsible for the transformation, I implemented the boundary of 2 concurrent task execution using a `BoundedSourceQueue` that creates an inner stream from the url of the task.
The inner stream is processed in parallel with factor 2.
This is probably not the best approach, but my knowledge of the `Akka streams` framework is limited.
The cancel functionality is achieved using a `KillSwitch` that will abort the inner stream and throw a specific exception that will allow the application to recover knowing that it was canceled and not failed.
A more robust solution could be probably achieved using a message bus (like Apache Kafka), but I decided to create an implementation with as less dependencies as possible. 

### Database Schema

For this task I used an in-memory map for the tasks and a PostgreSQL for the converted JSON data.

* Json data are stored in `json_line` table, each csv-converted line in a record. The actual data is stored in a `text` column. A `JSONB` column is more suitable, but `Slick` does not support it directly, but via another library, so I decided at the time being not to dedicate time for this.
  Each JSON line has information if it belongs to a completed task (using the `is_complete` boolean column) and the task info.
  Probably a Document DB, like `MongoDB`, or `ElasticSearch` for also supporting text-based search would be more suitable, but there was no need to add more complexity at the moment.
  Although, when requested the lines are returned ordered by time created, we cannot guarantee that they are in the same order as in the CSV file, due to the nature of the transformation, and they shouldn't be, as a JSON array cannot guarantee the correct order as well.
* A task has an id (randomly generated `UUID`), the number of lines processed, its state and the total processing time. 
  When the application starts, it loads in memory all the successfully completed tasks. I decided to exclude the ones that were not completed, as I cannot deduct correctly their status,
  and again, `Slick` did not directly support some PG capabilities, and more specifically the `bool_and` aggregate function. I could achieve this result in application level, but since it was not a prerequisite, I decided not to implement it.

### Prerequisites

The project requires 
* [JDK 11](https://www.openlogic.com/openjdk-downloads)
* [Scala 2.13](https://www.scala-lang.org/download/2.13.0.html)
* [sbt 1.5.8](https://www.scala-sbt.org/download.html)
* [docker](https://docs.docker.com/get-docker/)
* [docker-compose](https://docker-docs.netlify.app/compose/install/)

### Usage

#### Compile, run, test
You can use `make` commands to compile, run and test the app:

| Command                 | Description                                                                                                            |
|-------------------------|------------------------------------------------------------------------------------------------------------------------|
| ``make start-services`` | Starts a dockerized PostgreSQL for both testing and dev environment. There is no need to execute any SQL from the user |
| ``make compile``        | Cleans and compiles the project                                                                                        |
| ``make test``           | Starts the dockerized PostgreSQL, compiles the project and runs the tests                                              |
| ``make run``            | Starts the application                                                                                                 |

#### Endpoints

All endpoints are using the `JSON` format for both requests and responses

|Protocol|Path    |Description                                       | Request example | Response example     |
|--------|--------|--------------------------------------------------|-----------------|----------------------|
| `GET`  | `ready`| Responds if the app is ready to receive requests |                 | `{"isReady": true}`  |               

### Docker

### Known issues

Database testing was a difficult topic, as slick does not seem to be really easy to use for this task.
More specifically, I tried various implementations for rollbacking transactions after each test, returning to savepoints, etc.
but none seemed to work, as implicit values seem to be the problem.
I decided to go with a simple solution, as the database needs are simple as well and delete all records before and after each test.
I had to do it for both before and each, as there were flows running until the test `ActorSystem` was terminated, inserting records in the database.
This is not really an issue, as I am using another database within PG for tests.