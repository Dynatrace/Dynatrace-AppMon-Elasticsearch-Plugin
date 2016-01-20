#!/bin/sh

for i in test*.gradle;do
  ./gradlew clean && \
  echo Checking with file $i && \
  ./gradlew -b $i check
  if [ $? -ne 0 ];then
    exit 1
  fi
done
