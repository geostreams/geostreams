#!/bin/bash

# exit on error, with error code
set -e

# can use the following to push to NCSA registry for testing:
# BRANCH="master" SERVER=hub.ncsa.illinois.edu/ ./release.sh

# use DEBUG=echo ./release.sh to print all commands
export DEBUG=${DEBUG:-""}

# use SERVER=XYZ/ to push to a different server
SERVER=${SERVER:-""}

# what branch are we on
BRANCH=${BRANCH:-"$(git rev-parse --abbrev-ref HEAD)"}

# make sure docker is build
${DEBUG} $(dirname $0)/docker.sh

# find out the version
if [ "${BRANCH}" = "master" ]; then
    VERSION=${VERSION:-"3.0.0-alpha latest"}
elif [ "${BRANCH}" = "develop" ]; then
    VERSION="develop"
else
#    exit 0
    VERSION=${VERSION:-"3.0.0-alpha latest"}
fi

# tag all images and push if needed
for v in ${VERSION}; do
    if [ "$v" != "latest" -o "$SERVER" != "" ]; then
        ${DEBUG} docker tag geostreams/geostreams-api:latest ${SERVER}geostreams/geostreams-api:${v}
    fi
    ${DEBUG} docker push ${SERVER}geostreams/geostreams-api:${v}
done