#!/bin/bash

# start right job
case $1 in
    "initialize" )
        echo "Creating new database schema..."
        psql -h postgres -p 5432 -U postgres -c "CREATE ROLE geostreams WITH LOGIN CREATEDB NOSUPERUSER NOCREATEROLE PASSWORD 'geostreams'"
        psql -h postgres -p 5432 -U postgres -c "CREATE DATABASE geostreams WITH OWNER geostreams"
        psql -h postgres -p 5432 -U postgres geostreams < data/geostreams.sql
        ;;
    "deletedata" )
        echo "Dropping database, good luck."
        psql -h postgres -p 5432 -U postgres -c "DROP DATABASE geostreams;"
        ;;
    "server" )
        echo "Starting service..."
        exec ./bin/geostreams.sh -Dconfig.file=/home/geostreams/conf/application.conf
        ;;
    "help" )
        echo "initialize : create a new database and initialize with all data from server 0"
        echo "server     : runs the geostreams API"
        echo "help       : this text"
        echo ""
        echo "Default is ??"
        ;;
    * )
        exec "$@"
        ;;
esac