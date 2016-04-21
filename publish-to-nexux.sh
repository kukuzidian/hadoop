#!/bin/bash

PUBLISH_PROFILES="-Pnative -DskipTests"

echo "Publishing Hadoop to nexus"
mvn clean deploy $PUBLISH_PROFILES
