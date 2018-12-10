#!/usr/bin/env bash
#
# Data QC pipeline
#
. /etc/profile
APPNAME=DataQCPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

$APPDIR/run.sh --annotations
