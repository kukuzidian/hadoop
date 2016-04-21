#!/bin/bash

PUBLISH_PROFILES="-Pnative -DskipTests -Psources -Pjavadoc"

echo "Publishing Hadoop to nexus"
mvn clean deploy $PUBLISH_PROFILES
