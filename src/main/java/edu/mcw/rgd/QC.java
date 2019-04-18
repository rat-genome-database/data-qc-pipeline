package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mtutaj
 * @since 07/20/2018
 */
public class QC {

    private DAO dao = new DAO();
    private String version;

    Logger log = Logger.getLogger("core");

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

        boolean qcAnnotations = false;
        boolean qcRsOntology = false;
        boolean qcSequences = false;

        // no arguments means to run all qc types
        if( args.length==0 ) {
            args = new String[]{"--all"};
        }
        for (String arg : args) {
            switch (arg) {
                case "--all":
                    qcAnnotations = qcSequences = qcRsOntology = true;
                    break;
                case "--annotations":
                    qcAnnotations = true;
                    break;
                case "--rs_ontology":
                    qcRsOntology = true;
                    break;
                case "--sequences":
                    qcSequences = true;
                    break;
            }
        }

        if( qcAnnotations ) {
            qcAnnotationsWithMmoNotes();
            qcNDAnnotations();
            qcNewLinesInAnnotNotes();
            qcAnnotationsWithInactiveReferences();
        }

        if( qcRsOntology ) {
            qcRsOntology();
        }

        if( qcSequences ) {
            qcSequences();
        }
    }

    void qcAnnotationsWithMmoNotes() throws Exception {

        int issueCount = 0;
        issueCount += qcAnnotationsWithMmoNotes(SpeciesType.HUMAN);
        issueCount += qcAnnotationsWithMmoNotes(SpeciesType.MOUSE);
        issueCount += qcAnnotationsWithMmoNotes(SpeciesType.RAT);

        System.out.println();
        System.out.println("CC manual annotations with problematic MMO notes: "+issueCount);
    }

    int qcAnnotationsWithMmoNotes(int speciesTypeKey) throws Exception {

        Logger logMMO = Logger.getLogger("annots_with_MMO_issues");

        int issueCount = 0;
        List<Annotation> annots = dao.getRgdManualAnnotationsWithMmoNotes(speciesTypeKey);
        for( Annotation a: annots ) {
            String notes = a.getNotes();
            int mmoTermPos = notes.indexOf("MMO:");
            while( mmoTermPos>=0 ) {
                // see if MMO term matches MMO:xxxxxxx
                if( mmoTermPos+11 > a.getNotes().length() ) {
                    logMMO.info("  truncated MMO id for annotation with key="+a.getKey());
                    issueCount++;
                    break;
                }
                // see if MMO term is active
                String mmoTermAcc = notes.substring(mmoTermPos, mmoTermPos+11);
                Term t = dao.getTerm(mmoTermAcc);
                if( t==null ) {
                    logMMO.info("  invalid MMO term acc "+mmoTermAcc+" for annotation with key="+a.getKey());
                    issueCount++;
                } else if( t.isObsolete() ) {
                    logMMO.info("  inactive MMO term acc "+mmoTermAcc+" for annotation with key="+a.getKey());
                    issueCount++;
                }

                // go to next MMO: term acc
                mmoTermPos = notes.indexOf("MMO:", mmoTermPos+11);
            }
        }
        return issueCount;
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

    void qcNDAnnotations() throws Exception {

        // GO BP
        qcNDAnnotations("BP", "P");
        // GO CC
        qcNDAnnotations("CC", "C");
        // GO MF
        qcNDAnnotations("MF", "F");
    }

    void qcNDAnnotations(String ontologyId, String aspect) throws Exception {

        System.out.println();

        List<Annotation> ndAnnots = dao.getNDAnnotationsForRootTerm(ontologyId);
        System.out.println("QC ND annotations for "+ontologyId+" ontology (aspect "+aspect+") : "+ndAnnots.size());

        List<Annotation> ndAnnotsForDelete = new ArrayList<>();
        for( Annotation ndAnnot: ndAnnots ) {
            int rgdId = ndAnnot.getAnnotatedObjectRgdId();
            List<Annotation> manualAnnots = dao.getRgdManualAnnotations(rgdId, aspect);
            if( !manualAnnots.isEmpty() ) {
                System.out.println("  "+manualAnnots.size() + " RGD:"+rgdId);
                ndAnnotsForDelete.add(ndAnnot);
            }
        }

        if( ndAnnotsForDelete.isEmpty() ) {
            System.out.println("  all ND annotations for " + ontologyId + " ontology (aspect " + aspect + ") are valid");
        } else {
            dao.deleteNDAnnotations(ndAnnotsForDelete);
            System.out.println("  "+ ndAnnotsForDelete.size() + " ND annotations for " + ontologyId + " ontology (aspect " + aspect + ") have been deleted");
        }
    }

    void qcAnnotationsWithInactiveReferences() throws Exception {

        System.out.println();
        List<Annotation> annots = dao.getAnnotationsWithInactiveReferences();
        for( Annotation a: annots ) {
            System.out.println("    "+a.dump("|"));
        }
        System.out.println("ANNOTATIONS WITH INACTIVE REF_RGD_IDS: " +annots.size());
    }

    /**
     * RS ontology must have 'RGD ID:' synonyms properly formatted in order to be properly associated with a strain object
     * @throws Exception
     */
    void qcRsOntology() throws Exception {

        List<TermSynonym> malformedRsSynonyms = dao.getMalformedRsSynonyms();
        System.out.println();
        System.out.println("MALFORMED RS SYNONYMS: "+malformedRsSynonyms.size());
        if( !malformedRsSynonyms.isEmpty() ) {
            System.out.println();
            for( TermSynonym ts: malformedRsSynonyms ) {
                System.out.println("TERM_ACC:"+ts.getTermAcc()+"  SYN: ["+ts.getName()+"]");
            }
        }
    }

    void qcSequences() throws Exception {

        System.out.println();
        int count = dao.getCountOfOrphanedSequences();
        System.out.println("ORPHANED SEQUENCES: "+count);

        System.out.println();
        List<Integer> rgdIds = dao.getRgdIdsWithMultipleSequences("uniprot_seq");
        if( rgdIds.isEmpty() ) {
            System.out.println("NO RGD IDS WITH MULTIPLE uniprot_seq SEQUENCES");
        } else {
            System.out.println(+rgdIds.size()+" RGD IDS WITH MULTIPLE uniprot_seq SEQUENCES");
            for( Integer rgdId: rgdIds ) {
                System.out.println("    "+rgdId);
            }
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

