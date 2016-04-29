#!/bin/bash

PUBLISH_PROFILES="-Pnative -DskipTests -Pdocs -Psrc"
:q!

echo "Publishing Hadoop to nexus"

mvn clean deploy $PUBLISH_PROFILES
