# Geostreams

Geostreams API is a RESTful web service to manage data streams in time and space. It relies on 
[PostgreSQL](https://www.postgresql.org/), [PostGIS](http://postgis.net/) and the
[jsonb](https://www.postgresql.org/docs/current/static/datatype-json.html) data type. The web application is developed 
using the [Play Framework](https://www.playframework.com/) in [Scala](https://www.scala-lang.org/).

The previous version was available in [Clowder](https://clowder.ncsa.illinois.edu/). This version is standalone to simplify 
dependency management and deployment. It updates many of the dependencies and cleans up much of the code.

## Developers

You need to create a file `conf/application.conf` before running the application locally.
The values from `conf/reference.conf` will be used as the default configuration and can be overwritten in `conf/application.conf` (particularly
values pertaining to database should be changed as necessary). Also, you should verify the ports in 
`DockerFile` and `docker-compose.yml` are available or change if required. 

A key step is to configure the database. You will need to connect to the Postgres server and create a 
database. The details of this database would be used to fill the corresponding values in `conf/application.conf`. 

To build docker container image: ```./docker.sh```


### Docker Compose

To run full stack using Docker compose run: ```docker-compose up```. This requires the postgresql
database to be initialized. See Setting up Postgres Database section how to do that.

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

### Setting up Postgres Database
Connect to database as root user 
`psql -h localhost -p 5432 -U postgres `
Run following Commands 
```     
    CREATE USER geostreams;
    CREATE DATABASE geostream WITH OWNER geostreams;
```
End database connection and run following command in terminal 

`psql -h localhost -p 5432 -U postgres -d geostream -f geostreams.sql`

Add user through http://localhost:9000/signup and manually set emailconfirmed true in database  

`UPDATE users SET emailconfirmed = 't' where email='example@gmail.com';`