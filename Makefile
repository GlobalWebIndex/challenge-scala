###  start-service         : Start a PG instance to run with the app
.PHONY: start-services
start-services:
	@docker-compose up -d

###  run         			: Run the app
.PHONY: run
run: start-services
	@sbt "project server; run"

###  compile         		: Compile the project
.PHONY: compile
compile:
	@sbt clean
	@sbt compile

###  test         			: Run tests
.PHONY: test
test: start-services compile
	@sbt test