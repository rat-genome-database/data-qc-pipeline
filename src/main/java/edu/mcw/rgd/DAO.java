package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.dao.spring.IntListQuery;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * @author mtutaj
 * @since 07/20/18
 * <p>
 * wrapper to handle all DAO code
 */
public class DAO {

    AnnotationDAO adao = new AnnotationDAO();
    XdbIdDAO xdao = new XdbIdDAO();

    Log logUpdatedAnnots = LogFactory.getLog("updatedAnnots");

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

    /**
     * return external ids for given xdb key and rgd-id
     * @param xdbKey - external database key (like 2 for PubMed)
     * @param rgdId - rgd-id
     * @return list of external ids
     */
    public List<XdbId> getXdbIdsByRgdId(int xdbKey, int rgdId) throws Exception {
        return xdao.getXdbIdsByRgdId(xdbKey, rgdId);
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
