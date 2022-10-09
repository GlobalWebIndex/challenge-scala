# docker build -f Dockerfile -t csv2json:prod .
# docker run -it --rm -p 8080:8080 csv2json:prod

FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 AS build
WORKDIR /home
COPY . .
RUN sbt assembly

FROM openjdk:17
WORKDIR /home
COPY --from=build /home/target/scala-2.13/csv2json-assembly-0.0.1-SNAPSHOT.jar server.jar
EXPOSE 8080
CMD ["java", "-jar", "server.jar"]