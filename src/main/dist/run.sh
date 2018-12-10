#!/usr/bin/env bash
#
# Data QC pipeline
#
. /etc/profile
APPNAME=DataQCPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar "$@" 2>&1 | tee run.log

