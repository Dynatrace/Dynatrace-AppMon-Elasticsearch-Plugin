#!/bin/sh

for i in test*.gradle;do
  echo Checking with file $i
  ./gradlew clean && ./gradlew -b $i check || exit 1
done
