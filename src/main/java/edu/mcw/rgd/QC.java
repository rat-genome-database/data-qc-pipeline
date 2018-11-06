package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.ontology.Annotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.List;

/**
 * @author mtutaj
 * @since 07/20/2018
 */
public class QC {

    private DAO dao = new DAO();
    private String version;

    Log log = LogFactory.getLog("core");

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        QC manager = (QC) (bf.getBean("manager"));
        System.out.println(manager.getVersion());

        try {
            manager.run(args);
        }catch (Exception e) {
            manager.log.error(e);
            throw e;
        }
    }

    public void run(String[] args) throws Exception {

        qcNewLinesInAnnotNotes();
        qcOrphanedSequenceRgdIds();
    }

    void qcNewLinesInAnnotNotes() throws Exception {

        List<Annotation> annots = dao.getAnnotationsWithNewLinesInNotes();
        for( Annotation a: annots ) {
            String oldNotes = a.getNotes();
            int oldLen = oldNotes.length();
            String newNotes = a.getNotes().replaceAll("[\\s]+", " ").trim();
            a.setNotes(newNotes);
            int newLen = newNotes.length();
            System.out.println("\r\nOLD ["+oldNotes+"]\r\nNEW ["+newNotes+"]");
            if( newLen < oldLen-2 ) {
                System.out.println("  big deletion");
            }
            dao.updateAnnotation(a);
        }
        System.out.println();
        System.out.println("ANNOTATIONS WITH NEW LINES IN NOTES, FIXED: " +annots.size());
    }

    void qcOrphanedSequenceRgdIds() throws Exception {

        System.out.println();
        int count = dao.getCountOfOrphanedSequences();
        System.out.println(" ORPHANED SEQUENCES: "+count);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

