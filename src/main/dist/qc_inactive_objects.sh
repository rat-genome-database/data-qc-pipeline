#!/usr/bin/env bash
#
# Data QC pipeline
#
. /etc/profile
APPNAME=DataQCPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

INACTIVE_IDS_EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    INACTIVE_IDS_EMAIL_LIST=sjwang@mcw.edu,gthayman@mcw.edu,mtutaj@mcw.edu
fi

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

$APPDIR/run.sh --inactive_objects

# if there are any annotations with MMO issues in the notes, email them
if [ -s logs/qtls_with_inactive_markers_simple.log ]; then
  mailx -s "[$SERVER] qtls with inactive markers" $INACTIVE_IDS_EMAIL_LIST < logs/qtls_with_inactive_markers_simple.log
fi


