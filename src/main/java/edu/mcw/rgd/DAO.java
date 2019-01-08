package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.spring.EvidenceQuery;
import edu.mcw.rgd.dao.spring.IntListQuery;
import edu.mcw.rgd.dao.spring.ontologyx.TermSynonymQuery;
import edu.mcw.rgd.datamodel.EvidenceCode;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.annotation.Evidence;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author mtutaj
 * @since 07/20/18
 * <p>
 * wrapper to handle all DAO code
 */
public class DAO {

    AnnotationDAO adao = new AnnotationDAO();
    OntologyXDAO odao = new OntologyXDAO();

    Logger logUpdatedAnnots = Logger.getLogger("updatedAnnots");
    Logger logDeletedNDAnnots = Logger.getLogger("deleted_ND_annots");

    public DAO() {
        System.out.println(adao.getConnectionInfo());
    }

    public List<Annotation> getAnnotationsWithNewLinesInNotes() throws Exception {

        String sql = "SELECT * FROM full_annot WHERE dbms_lob.instr(notes,CHR(10))>0";
        return adao.executeAnnotationQuery(sql);
    }

    public void updateAnnotation(Annotation annot) throws Exception {
        logUpdatedAnnots.debug(annot.dump("|"));
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
}
