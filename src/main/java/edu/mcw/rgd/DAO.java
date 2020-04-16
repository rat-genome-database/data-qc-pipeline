package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.EvidenceQuery;
import edu.mcw.rgd.dao.spring.IntListQuery;
import edu.mcw.rgd.dao.spring.IntStringMapQuery;
import edu.mcw.rgd.dao.spring.StringListQuery;
import edu.mcw.rgd.dao.spring.ontologyx.TermSynonymQuery;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.annotation.Evidence;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.Map;

/**
 * @author mtutaj
 * @since 07/20/18
 * <p>
 * wrapper to handle all DAO code
 */
public class DAO {

    AliasDAO aliasDAO = new AliasDAO();
    AnnotationDAO adao = new AnnotationDAO();
    AssociationDAO assocDAO = new AssociationDAO();
    OntologyXDAO odao = new OntologyXDAO();
    QTLDAO qdao = new QTLDAO();
    ReferenceDAO rdao = new ReferenceDAO();

    Logger logUpdatedAnnots = Logger.getLogger("updatedAnnots");
    Logger logDeletedNDAnnots = Logger.getLogger("deleted_ND_annots");

    public String getConnectionInfo() {
        return adao.getConnectionInfo();
    }


    /// ALIASES

    public List<Alias> getRedundantGeneAliases() throws Exception {
        return aliasDAO.getRedundantGeneAliases();
    }

    public int deleteAliases(List<Alias> aliasesForDelete) throws Exception {
        aliasDAO.deleteAliases(aliasesForDelete);
        // aliasDAO.deleteAliases() returns count of rows deleted, but it is buggy: the count is negative, and double the actual count
        return aliasesForDelete.size();
    }


    public List<Annotation> getAnnotationsWithNewLinesInNotes() throws Exception {

        String sql = "SELECT * FROM full_annot WHERE dbms_lob.instr(notes,CHR(10))>0";
        return adao.executeAnnotationQuery(sql);
    }

    public void updateAnnotation(Annotation annot, String oldNotes, String newNotes) throws Exception {
        logUpdatedAnnots.debug(annot.dump("|"));
        logUpdatedAnnots.debug("     OLD NOTES: "+oldNotes);
        logUpdatedAnnots.debug("     NEW NOTES: "+newNotes);
        adao.updateAnnotation(annot);
    }

    public void deleteNDAnnotations(List<Annotation> ndAnnots) throws Exception {
        List<Integer> keys = new ArrayList<>(ndAnnots.size());
        for( Annotation a: ndAnnots ) {
            logDeletedNDAnnots.info(a.dump("|"));
            keys.add(a.getKey());
        }
        adao.deleteAnnotations(keys);
    }

    Set<String> getManualEvidenceCodes() throws Exception {
        if( _manualEvidenceCodes!=null ) {
            return _manualEvidenceCodes;
        }

        String sql = "SELECT * FROM evidence_codes";
        EvidenceQuery q = new EvidenceQuery(adao.getDataSource(), sql);
        q.compile();
        _manualEvidenceCodes = new HashSet<>();
        for( Evidence ev: (List<Evidence>)q.execute()) {
            String evCode = ev.getEvidence();
            if( EvidenceCode.isManualInSameSpecies(evCode) ) {
                _manualEvidenceCodes.add(evCode);
            }
        }
        return _manualEvidenceCodes;
    }
    static Set<String> _manualEvidenceCodes = null;


    public List<Annotation> getNDAnnotationsForRootTerm(String ontId) throws Exception {
        String rootTermAcc = odao.getRootTerm(ontId);

        List<Annotation> annots = adao.getAnnotations(rootTermAcc);
        Iterator<Annotation> it = annots.iterator();
        while( it.hasNext() ) {
            Annotation a = it.next();
            if( !a.getEvidence().equals("ND") ) {
                it.remove();
            }
        }
        return annots;
    }

    public List<Annotation> getRgdManualAnnotations(int rgdId, String aspect) throws Exception {

        Set<String> manualEvidenceCodes = getManualEvidenceCodes();

        List<Annotation> annots = adao.getAnnotationsByAspect(rgdId, aspect);
        Iterator<Annotation> it = annots.iterator();
        while( it.hasNext() ) {
            Annotation a = it.next();
            if( !manualEvidenceCodes.contains(a.getEvidence()) ) {
                it.remove();
                continue;
            }
            if( !a.getDataSrc().equals("RGD") ) {
                it.remove();
            }
        }
        return annots;
    }

    public List<Annotation> getRgdManualAnnotationsWithMmoNotes(int speciesTypeKey) throws Exception {

        List<Annotation> annots = adao.getAnnotationsBySpeciesAspectAndSource(speciesTypeKey, "C", "RGD");
        Iterator<Annotation> it = annots.iterator();

        while (it.hasNext()) {
            Annotation a = it.next();

            // handle only GENES
            if (a.getRgdObjectKey() != RgdId.OBJECT_KEY_GENES) {
                it.remove();
                continue;
            }
            // process only original annotations - WITH_INFO must be NULL
            if (a.getWithInfo() != null) {
                it.remove();
                continue;
            }
            // allowed evidence codes: IDA, IMP, IPI
            if (!(a.getEvidence().equals("IDA") || a.getEvidence().equals("IMP") || a.getEvidence().equals("IPI"))) {
                it.remove();
                continue;
            }
            // skip annotations that do not have MMO ids in the notes
            if (a.getNotes() == null || !a.getNotes().contains("MMO:")) {
                it.remove();
                continue;
            }
        }
        return annots;
    }

    public List<TermSynonym> getMalformedRsSynonyms() throws Exception {
        String sql = "SELECT * FROM ont_synonyms WHERE term_acc LIKE 'RS%' AND synonym_name LIKE '%RGD%ID%'\n" +
                " AND NOT regexp_like(synonym_name, '^RGD ID: [0-9]+$')";
        TermSynonymQuery q = new TermSynonymQuery(odao.getDataSource(), sql);
        return odao.execute(q);
    }

    public Term getTerm(String termAcc) throws Exception {
        return odao.getTermWithStatsCached(termAcc);
    }

    /**
     * get list of RGD IDS having multiple sequences of given type per RGD ID
     * @param seqType sequence type
     * @return list of rgd ids, possibly empty
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<Integer> getRgdIdsWithMultipleSequences(String seqType) throws Exception {
        String sql = "SELECT rgd_id FROM rgd_sequences WHERE seq_type=? "+
            "GROUP BY rgd_id "+
            "HAVING COUNT(*)>1";
        return IntListQuery.execute(adao, sql, seqType);
    }

    public int getCountOfOrphanedSequences() throws Exception {

        String sql = "SELECT COUNT(*) FROM seq_data WHERE NOT EXISTS "
                +"(SELECT 1 FROM rgd_sequences r WHERE data_md5=seq_data_md5)";
        return adao.getCount(sql);
    }

    public List<Annotation> getAnnotationsWithInactiveReferences() throws Exception {
        String sql = "SELECT a.*,r.species_type_key FROM full_annot a,rgd_ids r"+
            " WHERE annotated_object_rgd_id=rgd_id AND object_status='ACTIVE'"+
            "  AND EXISTS (SELECT 1 FROM rgd_ids r WHERE ref_rgd_id=rgd_id AND object_status<>'ACTIVE')";
        return adao.executeAnnotationQuery(sql);
    }

    public List<QTL> getActiveQtls() throws Exception {
        return qdao.getActiveQTLs();
    }

    public List<IntStringMapQuery.MapPair> getActiveQtlsWithInactiveMarkers() throws Exception {
        String sql = "SELECT q.rgd_id,q.qtl_symbol FROM qtls q\n" +
                "WHERE EXISTS(select 1 FROM rgd_ids r where (r.rgd_id=q.peak_rgd_id or r.rgd_id=flank_1_rgd_id or r.rgd_id=flank_2_rgd_id) and r.object_status<>'ACTIVE') \n" +
                "  AND EXISTS(select 1 from rgd_ids i where i.rgd_id=q.rgd_id and i.object_status='ACTIVE')";
        return IntStringMapQuery.execute(adao, sql);
    }

    public Set<Integer> getRefRgdIdsForRelatedQtls(int qtlKey) throws Exception {
        Map<Integer, String> relQtls = assocDAO.getQtlToQtlAssociations(qtlKey);
        if( relQtls.isEmpty() ) {
            return null;
        }
        Set<Integer> refRgdIds = new HashSet<>();
        for( String str: relQtls.values() ) {
            int pos1 = str.indexOf("||");
            int pos2 = str.indexOf("||", pos1+1);
            int pos3 = str.indexOf("||", pos2+1);
            String refRgdIdStr = str.substring(pos2+2, pos3);
            int refRgdId = Integer.parseInt(refRgdIdStr);
            refRgdIds.add(refRgdId);
        }
        return refRgdIds;
    }

    public Reference getReference(int refRgdId) throws Exception {
        return rdao.getReferenceByRgdId(refRgdId);
    }

    public int insertReferenceAssociation(int refKey, int qtlRgdId) throws Exception {
        return assocDAO.insertReferenceAssociationByKey(refKey, qtlRgdId);
    }

    public List<String> getDuplicateHgncIds() throws Exception {
        String sql = "SELECT x1.acc_id||'  RGD:'||x1.rgd_id||', RGD:'||x2.rgd_id ids\n" +
            "FROM rgd_acc_xdb x1,rgd_acc_xdb x2,rgd_ids i1,rgd_ids i2\n" +
            "WHERE x1.xdb_key=21 and x2.xdb_key=21 and x1.acc_id=x2.acc_id and x1.rgd_id<x2.rgd_id\n" +
            "  AND i1.rgd_id=x1.rgd_id and i1.object_key=1 and i1.object_status='ACTIVE'\n" +
            "  AND i2.rgd_id=x2.rgd_id and i2.object_key=1 and i2.object_status='ACTIVE'";
        return StringListQuery.execute(adao, sql);
    }
}
