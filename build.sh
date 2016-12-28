#!/bin/sh

export JAVA_HOME=/usr/local/jdk1.8.0_65
export PATH=/usr/local/protobuf-2.5.0/bin:$PATH

mvn package -Pdist,native -Dtar -DskipTests -Dmaven.javadoc.skip=true
if [ $? -ne 0 ]; then
    exit -1
fi

if [ -d output ]; then
    rm -rf output
fi
mkdir output
cp hadoop-dist/target/hadoop-*.tar.gz output/
