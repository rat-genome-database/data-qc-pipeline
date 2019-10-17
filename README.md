# data-qc-pipeline
General purpose qc reports on data integrity.

# QC

1. ANNOTATIONS
    1. FULL_ANNOT.NOTES: new lines are not allowed: must be replaced with a single space
    2. removal of obsolete ND annotations, per RGDD-1529:
       if an object has an ND annotation, it cannot have any manual RGD annotations;
       but if it has some, ND annotation must be removed
    3. report all manual CC annotations that have invalid MMO term accession in the NOTES field
    4. report all active annotations having inactive references (REF_RGD_ID)

2. INACTIVE OBJECTS
    1. Report active qtls that have one or more position markers that are inactive

3. ONTOLOGIES
    1. Synonyms for RS ontology that contain text 'RGD ID' are checked if the RGD ID is properly formatted
       (it must be exactly in the format: 'RGD ID: rgdid', where 'rgdid' is a numeric value)

4. SEQUENCES
    1. show count of orphaned sequences: sequences from SEQ_DATA table not associated
       with a row in RGD_SEQUENCES table
    2. show integrity issues for uniprot sequences (there should be only one sequence
       of type 'uniprot_seq' per RGD_ID)

5. TRANSCRIPTS
    1. display orphaned transcript rgd ids (transcript rgd ids that are active in RGD_IDS table, but which do not have
       corresponding entries in TRANSCRIPTS table)