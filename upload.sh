#!/bin/bash

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
  EXTRAS="--settings=\".buildscript/settings.xml\""
else
  echo "Deploying to Sonatype OSS release staging repo..."
  TASK=org.apache.maven.plugins:maven-gpg-plugin:1.6:sign-and-deploy-file
  REPO_URL=https://oss.sonatype.org/service/local/staging/deploy/maven2/
  REPO_ID=sonatype-nexus-staging
  EXTRAS=""
fi

mvn $TASK $EXTRAS \
    -DgeneratePom=false \
    -DrepositoryId=$REPO_ID \
    -Durl=$REPO_URL \
    -DpomFile=upload-pom.xml \
    -Dfile=$AAR \
    -Djavadoc=$JAVADOC \
    -Dsources=$SOURCES
