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

    Log logUpdatedAnnots = LogFactory.getLog("updatedAnnots");

    public DAO() {
        System.out.println(xdao.getConnectionInfo());
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
     * Returns all active genes for given species. Results do not contain splices or alleles
     * @param speciesKey species type key
     * @return list of active genes for given species
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<Gene> getActiveGenes(int speciesKey) throws Exception {
        return gdao.getActiveGenes(speciesKey);
    }

    public int getCountOfOrphanedSequences() throws Exception {

        String sql = "SELECT COUNT(*) FROM seq_data WHERE NOT EXISTS "
                +"(SELECT 1 FROM rgd_sequences r WHERE data_md5=seq_data_md5)";
        return gdao.getCount(sql);
    }
}
