#!/bin/bash

GROUPID=com.squareup.duktape
ARTIFACTID=duktape-android
VERSION=0.9.0-SNAPSHOT
PACKAGING=aar

AAR=duktape/build/outputs/aar/duktape-release.aar
SOURCES=duktape/build/libs/duktape-sources.jar
JAVADOC=duktape/build/libs/duktape-javadoc.jar

set -e

CLEAN=1
while [ $# -ne 0 ]; do
  case "$1" in
    "--no-clean" )
      CLEAN=0
    ;;
    * )
      echo "Unknown command line argument '$1'."
      exit 1
    ;;
  esac

  shift
done

if [ $CLEAN -ne 0 ]; then
  ./gradlew clean
fi

./gradlew build

if [ ! -f $AAR -o ! -f $SOURCES -o ! -f $JAVADOC ]; then
  echo "Missing one of:\n* $AAR\n* $SOURCES\n* $JAVADOC"
  exit 1
fi

if [[ $VERSION == *"-SNAPSHOT" ]]; then
  echo "Deploying to Sonatype OSS snapshots repo..."
  TASK=org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy-file
  REPO_URL=https://oss.sonatype.org/content/repositories/snapshots/
  REPO_ID=sonatype-nexus-snapshots
else
  echo "Deploying to Sonatype OSS release staging repo..."
  TASK=org.apache.maven.plugins:maven-gpg-plugin:1.6:sign-and-deploy-file
  REPO_URL=https://oss.sonatype.org/service/local/staging/deploy/maven2/
  REPO_ID=sonatype-nexus-staging
fi

mvn $TASK \
    --settings=".buildscript/settings.xml" \
    -DgroupId=$GROUPID \
    -DartifactId=$ARTIFACTID \
    -Dversion=$VERSION \
    -DgeneratePom=true \
    -Dpackaging=$PACKAGING \
    -DrepositoryId=$REPO_ID \
    -Durl=$REPO_URL \
    -Dfile=$AAR \
    -Djavadoc=$JAVADOC \
    -Dsources=$SOURCES
