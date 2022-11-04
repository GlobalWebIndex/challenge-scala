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

### Database Schema

For this task I used an in-memory map for the tasks and a PostgreSQL for the converted JSON data.

* Json data are stored in `json_line` table, each csv-converted line in a record. The actual data is stored in a `text` column. A `JSONB` column is more suitable, but `Slick` does not support it directly, but via another library, so I decided at the time being not to dedicate time for this.
  Each JSON line has information if it belongs to a completed task (using the `is_complete` boolean column) and the task info.
  Although, when requested the lines are returned ordered by time created, we cannot guarantee that they are in the same order as in the CSV file, due to the nature of the transformation, and they shouldn't be, as a JSON array cannot guarantee the correct order as well.
* A task has an id (randomly generated `UUID`), the number of lines processed, its state and the total processing time. 
  When the application starts, it loads in memory all the successfully completed tasks. I decided to exclude the ones that were not completed, as I cannot deduct correctly their status,
  and again, `Slick` did not directly support some PG capabilities, and more specifically the `bool_and` aggregate function. I could achieve this result in application level, but since it was not a prerequisite, I decided not to implement it.

### Usage

### Docker