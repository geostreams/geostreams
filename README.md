# Geo-temporal API

***Work in progress***

Geospatial and temporal API backed by [PostgreSQL](https://www.postgresql.org/) using [PostGIS](http://postgis.net/) and 
[jsonb](https://www.postgresql.org/docs/current/static/datatype-json.html) data type. API developed using the 
[Play Framework](https://www.playframework.com/) in [Scala](https://www.scala-lang.org/).

Previous version available in [Clowder](https://clowder.ncsa.illinois.edu/). Version 2 is standalone to simplify 
dependency management and deployment. It updates many of the dependencies and cleans up much of the code.

**For Developers**
You need to create a file `conf/application.conf` before running the application locally.
The values from `conf/reference.conf` will be used as the default configuration and can be overriden by `conf/application.conf`