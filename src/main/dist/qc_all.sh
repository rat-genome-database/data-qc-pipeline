#!/usr/bin/env bash
#
# Data QC pipeline
#
. /etc/profile
APPNAME=DataQCPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
MMO_EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    EMAIL_LIST=mtutaj@mcw.edu,slaulede@mcw.edu
    MMO_EMAIL_LIST=mtutaj@mcw.edu,jrsmith@mcw.edu
fi

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

$APPDIR/run.sh --all

mailx -s "[$SERVER] Data QC Pipeline OK" $EMAIL_LIST < run.log

# if there are any ND annotations that have been deleted, email them
if [ -s logs/deleted_ND_annots_daily.log ]; then
  mailx -s "[$SERVER] deleted ND annotations" $EMAIL_LIST < logs/deleted_ND_annots_daily.log
fi

# if there are any annotations with MMO issues in the notes, email them
if [ -s logs/annots_with_MMO_issues_daily.log ]; then
  mailx -s "[$SERVER] annotations with MMO issues" $MMO_EMAIL_LIST < logs/annots_with_MMO_issues_daily.log
fi


