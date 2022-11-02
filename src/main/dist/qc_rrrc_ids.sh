#!/usr/bin/env bash
#
# Data QC pipeline: examine RRRC ID strain aliases and add them as XDB IDS if not present
#
. /etc/profile
APPNAME=DataQCPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    EMAIL_LIST=sjwang@mcw.edu,mtutaj@mcw.edu
fi

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

$APPDIR/run.sh --rrrc_ids

if [ -s logs/rrrc_ids_summary.log ]; then
  mailx -s "[$SERVER] RRRC IDs QC report" $EMAIL_LIST < logs/rrrc_ids_summary.log
fi


