# CSV to JSON App
An Akka Application (Akka-HTTP, Akka-Streams, Alpakka)

Build/Run with Sbt

## Example Usage
For testing purposes you can add delay time in application.conf
``` lombok.config
downloader.delay = 10s
```
to check if Application will run more than 2 tasks at once (Spoiler: Nope)
and to cancel a given task at any time.

---
Example Using [httpie](https://httpie.io/)

Get All Tasks:


``` shell
http localhost:8080/tasks
```

Response:
``` json
{
    "tasks": []
}
```

---
Create Task 1
``` shell
echo '{"csvUri": "https://data.cityofnewyork.us/api/views/7yay-m4ae/rows.csv?accessType=DOWNLOAD"}' | http post localhost:8080/tasks
```
Response
``` json
 {
    "csvUri": "https://data.cityofnewyork.us/api/views/83z6-smyr/rows.csv?accessType=DOWNLOAD",
    "id": 2,
    "jsonUri": null,
    "status": "running"
}
```

---
Create Task 2
``` shell
echo '{"csvUri": "https://data.cityofnewyork.us/api/views/83z6-smyr/rows.csv?accessType=DOWNLOAD"}' | http post localhost:8080/tasks
```
Response
``` json
 {
    "csvUri": "https://data.cityofnewyork.us/api/views/83z6-smyr/rows.csv?accessType=DOWNLOAD",
    "id": 2,
    "jsonUri": null,
    "status": "running"
}
```

---
Create Task 3
``` shell
echo '{"csvUri": "https://data.cityofnewyork.us/api/views/825b-niea/rows.csv?accessType=DOWNLOAD"}' | http post localhost:8080/tasks
```

Response
``` json
 {
    "csvUri": "https://data.cityofnewyork.us/api/views/83z6-smyr/rows.csv?accessType=DOWNLOAD",
    "id": 2,
    "jsonUri": null,
    "status": "running"
}

```

---
Get All Tasks:
``` shell
http localhost:8080/tasks
```

Response:
``` json
{
    {
    "tasks": [
        {
            "csvUri": "https://data.cityofnewyork.us/api/views/7yay-m4ae/rows.csv?accessType=DOWNLOAD",
            "id": 1,
            "jsonUri": null,
            "status": "running"
        },
        {
            "csvUri": "https://data.cityofnewyork.us/api/views/83z6-smyr/rows.csv?accessType=DOWNLOAD",
            "id": 2,
            "jsonUri": null,
            "status": "running"
        },
        {
            "csvUri": "https://data.cityofnewyork.us/api/views/825b-niea/rows.csv?accessType=DOWNLOAD",
            "id": 3,
            "jsonUri": null,
            "status": "scheduled"
        }
    ]
}
}
```

---
After app downloads and transforms csv to json the tasks will include a jsonUri which you can use


Get All Tasks:


``` shell
http localhost:8080/tasks
```
Response
``` json
{
    "tasks": [
        {
            "csvUri": "https://data.cityofnewyork.us/api/views/7yay-m4ae/rows.csv?accessType=DOWNLOAD",
            "id": 1,
            "jsonUri": "localhost:8080/files/1",
            "status": "done"
        },
        {
            "csvUri": "https://data.cityofnewyork.us/api/views/83z6-smyr/rows.csv?accessType=DOWNLOAD",
            "id": 2,
            "jsonUri": "localhost:8080/files/2",
            "status": "done"
        },
        {
            "csvUri": "https://data.cityofnewyork.us/api/views/825b-niea/rows.csv?accessType=DOWNLOAD",
            "id": 3,
            "jsonUri": "localhost:8080/files/3",
            "status": "done"
        }
    ]
}
```

---
Finally we can get our json file by just calling the jsonUri
``` shell
http localhost:8080/files/3
```

## Next Steps
- Add tests for `TaskActor` and `TaskRoutes`
- Validate given csv uri
- Dockerize application
- Retry HTTP Requests in case of failure
- Move all configuration to a different file
- Modify output folder `TaskActor#jsonUri` to be dynamic (and use server host/port) instead of hardcoded
- Remove null from json response (Circe dropNullValues)
- Remove previous json files on shutdown/startup


## Material Used
- Akka-HTTP / Akka-Streams / Alpakka documentation
- [Akka HTTP Quickstart](https://developer.lightbend.com/guides/akka-http-quickstart-scala/)
- [A Practical Introduction to Akka Streams by Jacek Kunicki](https://www.youtube.com/watch?v=L1FS2dyiwIg)
- [Akka-Streams KillSwitch](https://doc.akka.io/docs/akka/2.4/scala/stream/stream-dynamic.html#Controlling_graph_completion_with_KillSwitch)
- [Alpakka CSV](https://doc.akka.io/docs/alpakka/current/data-transformations/csv.html)
- [Alpakka: Process big CSV files using Akka Streams](https://medium.com/@knoldus/alpakka-process-big-csv-files-using-akka-streams-b764c8a64b01)
- [Alpakka: Fetch CSV via Akka HTTP and publish the data as JSON to Kafka](https://akka.io/alpakka-samples/http-csv-to-kafka/index.html#fetch-csv-via-akka-http-and-publish-the-data-as-json-to-kafka)
- [Akka HTTP Downloader example](https://gist.github.com/steinybot/a1f79fe9a67693722164)
- [Maximizing Throughput for Akka Streams](https://blog.colinbreck.com/maximizing-throughput-for-akka-streams/)
- [Akka HTTP & JSON (for Circe)](https://www.youtube.com/watch?v=yU85EowqhY4)
- [SourceQueue](https://stackoverflow.com/questions/30964824/how-to-create-a-source-that-can-receive-elements-later-via-a-method-call)
