#!/usr/bin/env bash
#
# Data QC pipeline: examine related qtls and add missing RGD_REF_RGD_ID entries (per RGDD-153);
#    what means, that on qtl report page, references listed in section 'Related Qtls' must be also listed in section 'Curated References'
#
. /etc/profile
APPNAME=data-qc-pipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    EMAIL_LIST=jrsmith@mcw.edu,mtutaj@mcw.edu
fi

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

$APPDIR/run.sh --related_qtls

if [ -s logs/related_qtls_summary.log ]; then
  mailx -s "[$SERVER] related qtls with missing RGD_REF_RGD_ID entries" $EMAIL_LIST < logs/related_qtls_summary.log
fi


