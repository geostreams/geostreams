# Geostreams

Geostreams API is a RESTful web service to manage data streams in time and space. It relies on 
[PostgreSQL](https://www.postgresql.org/), [PostGIS](http://postgis.net/) and the
[jsonb](https://www.postgresql.org/docs/current/static/datatype-json.html) data type. The web application is developed 
using the [Play Framework](https://www.playframework.com/) in [Scala](https://www.scala-lang.org/).

The previous version was available in [Clowder](https://clowder.ncsa.illinois.edu/). This version is standalone to simplify 
dependency management and deployment. It updates many of the dependencies and cleans up much of the code.

## Developers

You need to create a file `conf/application.conf` before running the application locally.
The values from `conf/reference.conf` will be used as the default configuration and can be overwritten in `conf/application.conf`.

To build docker container image: ```./docker.sh```

### Manual Docker Execution

Create a docker network if it doesn't exists already::

```docker network create geostreams```

To run just postgres:

```docker run -d --name postgres --network geostreams --volume postgres:/var/lib/postgresql/data -p 5432:5432 mdillon/postgis:9.5```

To run just the web application: 

```docker run --network geostreams --volume ${PWD}/conf/application.conf:/home/geostreams/conf/application.conf --link 96f80512956f:postgres -e APPLICATION_SECRET="somelongrandomstringhere" -p 9000:9000 geostreams/geostreams-api:3.0.0-alpha```

If not argument is passed into the docker container it will default to `server` and run the service. If `initialize`
is passed in the database will be initialized. For more information see `docker/entrypoint.sh`.

Check that everything is ok:

```curl http://localhost:9000/geostreams/api/status```


### Docker Compose

To run full stack using Docker compose run: ```docker-compose up```. This requires the postgresql
database to be initialized. See Manual Docker Execution section how to do that.