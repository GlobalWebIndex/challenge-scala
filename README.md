# Csv2Json

Csv2Json is a server of a task manager that transforms CSV to JSON.


## Table of Contents
- [Installation](#installation)
- [REST API](#rest-api)
- [Notes](#notes)

## Installation
There are two ways to run the application </br>
The first way is to call 'sbt run'. </br>
The other way is to run it from docker with these command lines
```bash
docker build -f Dockerfile -t csv2json:prod .
docker run -it --rm -p 8080:8080 csv2json:prod
```

## REST API
#### POST /task/
Creates a task with URL pointing to CSV dataset which will be converted to JSON and returns task ID.
   - 2 tasks can be run at the same time
   - the task is executed immediately if `running-tasks < 2` 

For example
```bash
curl -X POST http://localhost:8080/task \
      -H "Content-Type: application/json" \    
      -d '{"url": "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD"}'
```
```json
{
  "id": "6650c504-da23-48c8-81c0-7bf4bbd1304e",
  "url": "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
  "status": "SCHEDULED",
  "linesProcessed": 0,
  "avgLinesProcessed": 0
}
```

#### GET /task/
List tasks <br/>
For example
```bash
curl -X GET http://localhost:8080/task 
```
```json
[
  {
    "id": "3d5cc1ab-19bc-4e32-99d1-e3c5a6451b81",
    "url": "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
    "status": "DONE",
    "linesProcessed": 196218,
    "avgLinesProcessed": 3689
  },
  {
    "id": "e8453b8a-4c94-4e47-9ab7-44e9e99360ad",
    "url": "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
    "status": "RUNNING",
    "linesProcessed": 0,
    "avgLinesProcessed": 0
  },
  {
    "id": "cc54576f-2f5a-4da1-acb4-76cefb9050fa",
    "url": "https://bythenumbers.s",
    "status": "FAILED",
    "linesProcessed": 0,
    "avgLinesProcessed": 0
  },
  {
    "id": "d6767c7f-3381-4852-8380-90980aecd396",
    "url": "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
    "status": "CANCELED",
    "linesProcessed": 37819,
    "avgLinesProcessed": 1354
  }
]

```
#### GET /task/[taskId]
Returns information about the task:    
- lines processed
- avg lines processed (count/sec)
- state (`SCHEDULED/RUNNING/DONE/FAILED/CANCELED`)

It keeps the connection open until the task isn't in a terminal state and send the updated response every 2 seconds.

For example
```bash
curl -X GET http://localhost:8080/task/c97962a7-ec3c-4132-a8e8-72a18f4d2d46
```
```json
{
  "id" : "c97962a7-ec3c-4132-a8e8-72a18f4d2d46",
  "url" : "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
  "status" : "RUNNING",
  "linesProcessed" : 73957,
  "avgLinesProcessed" : 12278
}
{
  "id" : "c97962a7-ec3c-4132-a8e8-72a18f4d2d46",
  "url" : "https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
  "status" : "RUNNING",
  "linesProcessed" : 98739,
  "avgLinesProcessed" : 12387
}

```

#### GET /task/[taskId]/json
Downloads a JSON file generated from the task based on the CSV <br/>
For example
```bash
curl -X GET http://localhost:8080/task/c97962a7-ec3c-4132-a8e8-72a18f4d2d46/json | head
```
```json
{"City, State":"El Monte, CA","Subcategory":"Revenue","Category":"Operating Revenue","Form/Table":"TO_INCOME_STAT_OPREV","Line Description":"Passenger Fare for Transit Service","Row Number":"20171322159","Type":"Revenues","Value":"9971132","Fiscal Year":"2017","Entity Name":"Access Services for Los Angeles County CTSA - Specialized Service","Zip Code":"902493005"}
{"City, State":"El Monte, CA","Subcategory":"Revenue","Category":"Operating Revenue","Form/Table":"TO_INCOME_STAT_OPREV","Line Description":"Special Transit Fares","Row Number":"20171322158","Type":"Revenues","Value":"0","Fiscal Year":"2017","Entity Name":"Access Services for Los Angeles County CTSA - Specialized Service","Zip Code":"902493005"}
```

#### DELETE /task/[taskId]
Cancels a task. </br>
Tasks in `SCHEDULED` or `RUNNING` state can be canceled.
For example

```bash
curl -X DELETE http://localhost:8080/task/9a6687bc-1904-4b13-8713-cd64dd4e3d74
```
```json
{
  "id":"9a6687bc-1904-4b13-8713-cd64dd4e3d74",
  "url":"https://bythenumbers.sco.ca.gov/api/views/kswn-qt8j/rows.csv?accessType=DOWNLOAD",
  "status":"CANCELED",
  "linesProcessed":100616,
  "avgLinesProcessed":11693
}
```

## Notes

This project does not use Akka, only CE3, FS2 and Http4s. Furthermore, I am not a Scala developer. </br>
I started this project after having two courses on CE3 and FS2 for a week. <br/>
However, I didn't finish FS2 course (because of the deadline) and for that reason, </br>
I didn't know how to combine CSV stream with another stream to calculate counts/second. </br>
Moreover, I checked the server taking half a GB of memory and I don't know if it is a memory leak </br>
or normal for JVM programs. </br>
In conclusion, this project is not a good example of a senior Scala developer, </br> 
but I enjoyed every bit of it, and certainly I will retry the same exercise in  different programming languages.

