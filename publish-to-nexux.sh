#!/bin/bash

PUBLISH_PROFILES="-Pnative -DskipTests -Psources -Pjavadoc"

echo "Replacing groupID with com.didichuxing.hadoop"
find . -name pom.xml | \
  xargs -I {} sed -i -e "s/org.apache.hadoop/com.didichuxing.hadoop/g" {}

echo "Publishing Hadoop to nexus"
mvn clean deploy -Dgpg.passphrase=-Dgpg.skip $PUBLISH_PROFILES
