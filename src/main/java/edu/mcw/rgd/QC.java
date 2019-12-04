package edu.mcw.rgd;

import edu.mcw.rgd.dao.spring.IntStringMapQuery;
import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.QTL;
import edu.mcw.rgd.datamodel.Reference;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.*;

/**
 * @author mtutaj
 * @since 07/20/2018
 */
public class QC {

    private DAO dao = new DAO();
    private String version;

    Logger log = Logger.getLogger("core");
    Logger logQtls = Logger.getLogger("qtls_with_inactive_markers");
    Logger logRelatedQtls = Logger.getLogger("related_qtls");

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

        boolean qcAll = false;
        boolean qcAliases = false;
        boolean qcAnnotations = false;
        boolean qcInactiveObjects = false;
        boolean qcRelatedQtls = false;
        boolean qcRsOntology = false;
        boolean qcSequences = false;
        boolean qcTranscripts = false;

        // no arguments means to run all qc types
        if( args.length==0 ) {
            args = new String[]{"--all"};
        }
        for (String arg : args) {
            switch (arg) {
                case "--all":
                    qcAll = true;
                    break;
                case "--aliases":
                    qcAliases = true;
                    break;
                case "--annotations":
                    qcAnnotations = true;
                    break;
                case "--inactive_objects":
                    qcInactiveObjects = true;
                    break;
                case "--related_qtls":
                    qcRelatedQtls = true;
                    break;
                case "--rs_ontology":
                    qcRsOntology = true;
                    break;
                case "--sequences":
                    qcSequences = true;
                    break;
                case "--transcripts":
                    qcTranscripts = true;
                    break;
            }
        }

        if( qcAliases || qcAll ) {
            qcRedundantGeneAliases();
        }

        if( qcAnnotations || qcAll ) {
            qcAnnotationsWithMmoNotes();
            qcNDAnnotations();
            qcNewLinesInAnnotNotes();
            qcAnnotationsWithInactiveReferences();
        }

        if( qcInactiveObjects || qcAll ) {
            qcActiveQtlsWithInactiveMarkers();
        }

        if( qcRelatedQtls || qcAll ) {
            qcRelatedQtls();
        }

        if( qcRsOntology || qcAll ) {
            qcRsOntology();
        }

        if( qcSequences || qcAll ) {
            qcSequences();
        }

        if( qcTranscripts || qcAll ) {
            qcTranscripts();
        }
    }

    void qcRedundantGeneAliases() throws Exception {

        Logger logDeletedAliases = Logger.getLogger("deleted_aliases");

        List<Alias> aliases = dao.getRedundantGeneAliases();
        for( Alias a: aliases ) {
            logDeletedAliases.info(a.dump("|"));
        }

        int aliasesDeleted = dao.deleteAliases(aliases);
        System.out.println();
        System.out.println("deleted "+aliasesDeleted+" redundant gene aliases (aliases that were the same as gene name or symbol)");
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

        // TODO: display orphaned RefSeq Accs (nucleotide RefSeq acc xrefs without a mtahcing transcript object)
    }

    void qcActiveQtlsWithInactiveMarkers() throws Exception {
        System.out.println();
        List<IntStringMapQuery.MapPair> list = dao.getActiveQtlsWithInactiveMarkers();
        System.out.println("ACTIVE QTLS WITH INACTIVE MARKERS: "+list.size());

        if( !list.isEmpty() ) {
            logQtls.info("ACTIVE QTLS WITH INACTIVE MARKERS: " + list.size());
            logQtls.info("===");
            for (IntStringMapQuery.MapPair pair : list) {
                logQtls.info("RGD:"+pair.keyValue+"  "+pair.stringValue);
            }
        }
    }

    /**
     * add missing entries in RGD_REF_RGD_IDS table for RELATED_QTLS (per RGDD-153);
     * what means, that on qtl report page, references listed in section 'Related Qtls' must be also listed in 'Curated References'
     */
    void qcRelatedQtls() throws Exception {

        System.out.println();

        List<QTL> qtls = dao.getActiveQtls();
        Collections.shuffle(qtls);

        int qtlsWithRelatedQtlsTotal = 0;

        List<String> insertedEntries = new ArrayList<>();

        for( QTL q: qtls ) {
            Set<Integer> refRgdIds = dao.getRefRgdIdsForRelatedQtls(q.getKey());
            if( refRgdIds==null ) {
                continue;
            }
            qtlsWithRelatedQtlsTotal++;

            for( Integer refRgdId: refRgdIds ) {
                Reference ref = dao.getReference(refRgdId);
                if( ref.getReferenceType().equals("JOURNAL ARTICLE") || ref.getReferenceType().equals("ABSTRACT") ) {
                    int inserted = dao.insertReferenceAssociation(ref.getKey(), q.getRgdId());
                    if( inserted!=0 ) {
                        insertedEntries.add("  qtl "+q.getSymbol()+" RGD:"+q.getRgdId()+":  inserted reference association RGD:"+refRgdId);
                    }
                } else {
                    System.out.println("unexpected reference type: "+ref.getReferenceType()+" for QTL "+q.getSymbol()+", REF_RGD_ID:"+refRgdId);
                }
            }
        }
        System.out.println("ACTIVE QTLS WITH RELATED QTLS: "+qtlsWithRelatedQtlsTotal);
        System.out.println("  added reference associations for related qtls: "+insertedEntries.size());

        if( !insertedEntries.isEmpty() ) {
            logRelatedQtls.info("Added reference associations for related qtls: "+insertedEntries.size());
            for( String msg: insertedEntries ) {
                logRelatedQtls.info(msg);
            }
            logRelatedQtls.info("");
        }
    }

    void qcTranscripts() {


    }



    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

