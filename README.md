# Task execution system

#### Table of Contents
* [Endpoints](#endpoints)
* [Configuration](#configuration)
* [Running](#running)
* [Tests](#tests)
* [Libraries](#libraries)
    *  [http4s](#http4s)
* [Academic discussion over some choices](#academic-discussion-over-some-choices)
    *  [Why I used tier architecture?](#why-i-used-tier-architecture)
    *  [ Why I used http4s (library) over for example Play (framework)?](#why-i-used-http4s-library-over-for-example-play-framework)


## Endpoints
The rest endpoints listed below:

Method | Url                  | Description
------ | -----------          | -----------
GET    | /task                | Returns all tasks
GET    | /task/{id}/json      | Returns json for given task id
POST   | /task                | Creates a task. Pass csv uri in JSON body. Returns a 201 with the created task.
DELETE | /task/{id}           | Cancel the task with the specified id. Returns 404 when no task present with passed id.

Some example requests:

Create a task:
```curl -X POST --header "Content-Type: application/json" --data '{"scvUri": "/someUrl"}' http://localhost:8099/task```

Get all tasks from your local storage:
```curl http://localhost:8099/task```

Get json for task with given id (assuming the id of the task is 1):
```curl http://localhost:8099/task/1/json```

Cancel a task (assuming the id of the task is 1):
```curl -X DELETE http://localhost:8099/task/1```

## Configuration
All the configuration is stored in `application.conf`. By default, it listens to port number 8099.

## Running
You can run the microservice with `sbt run` or by importing that project into your IDE and starting it from there.

## Tests
For testing purposes I use ScalaTest. Unit tests are using mocks, while integration tests are using real HTTP client to make requests.
To run unit tests do `sbt test`. To run integration tests `sbt it:test`.

## Libraries
### http4s
For REST [http4s](http://http4s.org/) is used. It provides streaming and functional HTTP for Scala.
Project uses [cats-effect](https://github.com/typelevel/cats-effect) as an effect monad, what postpones side effect till the last moment.

http4s uses [fs2](https://github.com/functional-streams-for-scala/fs2) for streaming. This allows to return
streams in the HTTP layer so the response doesn't need to be generated in memory before sending it to the client.

## Academic discussion over some choices
### Why I used tier architecture?
I'm totally aware of drawbacks of the tier architecture and its heavy dependency on database data model. Someone might ask: Why didn't you use hexagonal architecture?
In production code I'd definitely consider that as we have integration with one external service, that might change to many external services in the future.
That's why ports & adapters would be beneficial here for example in case we want to merge that data or calculate it somehow.
Despite that I decided to not overengineer that sample application and sticked to tier architecture assuming, that task model will not evolve.
### Why I used http4s (library) over for example Play (framework)?
It is much better to have full control over what's happening in my application than rely on some runtime magic and annotations. That's why I've chosen http4s library over Play.
Might have used [tapir](https://github.com/softwaremill/tapir), [typedApi](https://github.com/pheymann/typedapi) or [akka-http](https://github.com/akka/akka-http) as well.



