#!/usr/bin/env bash
#
# Data QC pipeline
#
. /etc/profile
APPNAME=data-qc-pipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
ALLELE_EMAIL_LIST=mtutaj@mcw.edu
MMO_EMAIL_LIST=mtutaj@mcw.edu
INACTIVE_IDS_EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" == "REED" ]; then
    EMAIL_LIST=mtutaj@mcw.edu,slaulede@mcw.edu,jrsmith@mcw.edu
    ALLELE_EMAIL_LIST=mtutaj@mcw.edu,sjwang@mcw.edu,jrsmith
    MMO_EMAIL_LIST=mtutaj@mcw.edu,jrsmith@mcw.edu
    INACTIVE_IDS_EMAIL_LIST=sjwang@mcw.edu,gthayman@mcw.edu,mtutaj@mcw.edu
fi

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

$APPDIR/run.sh --all

mailx -s "[$SERVER] Data QC Pipeline OK" $EMAIL_LIST < logs/summary.log

# if there are any ND annotations that have been deleted, email them
if [ -s logs/deleted_ND_annots_daily.log ]; then
  mailx -s "[$SERVER] deleted ND annotations" $EMAIL_LIST < logs/deleted_ND_annots_daily.log
fi

# if there are any annotations with MMO issues in the notes, email them
if [ -s logs/annots_with_MMO_issues_daily.log ]; then
  mailx -s "[$SERVER] annotations with MMO issues" $MMO_EMAIL_LIST < logs/annots_with_MMO_issues_daily.log
fi

# if there are any qtls with inactive markers, email
if [ -s logs/qtls_with_inactive_markers_simple.log ]; then
  mailx -s "[$SERVER] qtls with inactive markers" $INACTIVE_IDS_EMAIL_LIST < logs/qtls_with_inactive_markers_simple.log
fi

# if there are any related qtls with missing RGD_REF_RGD_ID entries, email the report with fixes
if [ -s logs/related_qtls_summary.log ]; then
  mailx -s "[$SERVER] related qtls with missing RGD_REF_RGD_ID entries" $MMO_EMAIL_LIST < logs/related_qtls_summary.log
fi

# if there are gene alleles having the same symbols or names, email them
if [ -s logs/duplicate_alleles_simple.log ]; then
  mailx -s "[$SERVER] duplicate gene alleles" $ALLELE_EMAIL_LIST < logs/duplicate_alleles_simple.log
fi

# if there are phenotypic variants with same names, report them by email
if [ -s logs/duplicate_variants_simple.log ]; then
  mailx -s "[$SERVER] duplicate variant names" $ALLELE_EMAIL_LIST < logs/duplicate_variants_simple.log
fi

# if there are gene alleles having the same symbols or names, email them
if [ -s logs/orphan_terms_summary.log ]; then
  mailx -s "[$SERVER] orphan terms" mtutaj@mcw.edu < logs/orphan_terms_summary.log
fi

if [ -s logs/rrrc_ids_summary.log ]; then
  mailx -s "[$SERVER] RRRC IDs QC report" $ALLELE_EMAIL_LIST < logs/rrrc_ids_summary.log
fi
