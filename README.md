# data-qc-pipeline
General purpose qc reports on data integrity.

# QC

1. ANNOTATIONS
    1. FULL_ANNOT.NOTES: new lines are not allowed: must be replaced with a single space

2. SEQUENCES
    1. show count of orphaned sequences: sequences from SEQ_DATA table not associated
       with a row in RGD_SEQUENCES table
    2. show integrity issues for uniprot sequences (there should be only one sequence
       of type 'uniprot_seq' per RGD_ID)
