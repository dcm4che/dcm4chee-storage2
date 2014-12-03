#!/bin/bash
set -ev

mvn -P ossrh versions:update-properties -DincludeProperties=dcm4che.version
mvn -P ossrh install -DskipTests=true
