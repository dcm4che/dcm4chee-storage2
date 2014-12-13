#!/bin/bash
set -ev

if [[ ${RELEASE_CANDIDATE} == "true" ]]
then
  BUILD_ID=$(curl -s -H 'Accept: application/vnd.travis-ci.2+json' https://api.travis-ci.org/repos/dcm4che/dcm4chee-arc-cdi/builds | grep -o '^{"builds":\[{"id":[0-9]*' | grep -o '[0-9]*' | tr -d '\n')

  echo "BUILD_ID = ${BUILD_ID}"

  curl -H "Authorization: token ${TRAVIS_TOKEN}" -H 'Accept: application/vnd.travis-ci.2+json' -X POST \
    https://api.travis-ci.org/builds/${BUILD_ID}/restart
fi
