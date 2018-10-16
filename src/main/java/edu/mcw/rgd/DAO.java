package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.datamodel.Gene;
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
    GeneDAO gdao = new GeneDAO();
    XdbIdDAO xdao = new XdbIdDAO();

    Log logInserted = LogFactory.getLog("insertedIds");

    public DAO() {
        System.out.println(xdao.getConnectionInfo());
    }

    public List<Annotation> getAnnotationsWithNewLinesInNotes() throws Exception {

        String sql = "SELECT * FROM full_annot WHERE dbms_lob.instr(notes,CHR(10))>0";
        return adao.executeAnnotationQuery(sql);
    }

    public void updateAnnotation(Annotation annot) throws Exception {
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
     * Returns all active genes for given species. Results do not contain splices or alleles
     * @param speciesKey species type key
     * @return list of active genes for given species
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<Gene> getActiveGenes(int speciesKey) throws Exception {
        return gdao.getActiveGenes(speciesKey);
    }

    /**
     * insert a bunch of XdbIds; duplicate entries are not inserted (with same RGD_ID,XDB_KEY,ACC_ID,SRC_PIPELINE)
     * @param xdbs list of XdbIds objects to be inserted
     * @return number of actually inserted rows
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int insertXdbs(List<XdbId> xdbs) throws Exception {

        for( XdbId xdbId: xdbs ) {
            logInserted.debug(xdbId.dump("|"));
        }

        return xdao.insertXdbs(xdbs);
    }

    public int getCountOfOrphanedSequences() throws Exception {

        String sql = "SELECT COUNT(*) FROM sequences s WHERE NOT EXISTS "
                +"(SELECT 1 FROM rgd_seq_rgd_id r WHERE s.sequence_key=r.sequence_key)";
        return gdao.getCount(sql);
    }

    public int getCountOfOrphanedSequenceRgdIds() throws Exception {

        String sql = "SELECT COUNT(*) FROM rgd_ids r WHERE r.object_key=9 AND object_status='ACTIVE' AND NOT EXISTS "
                +"(SELECT 1 FROM sequences s WHERE s.rgd_id=r.rgd_id)";
        return gdao.getCount(sql);
    }
}
