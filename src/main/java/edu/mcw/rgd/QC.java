package edu.mcw.rgd;

import edu.mcw.rgd.dao.spring.IntStringMapQuery;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mtutaj
 * @since 07/20/2018
 */
public class QC {

    private DAO dao = new DAO();
    private String version;

    Logger log = LogManager.getLogger("status");
    Logger logQtls = LogManager.getLogger("qtls_with_inactive_markers");
    Logger logRelatedQtls = LogManager.getLogger("related_qtls");
    Logger logDuplicateAlleles = LogManager.getLogger("duplicate_alleles");

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        QC manager = (QC) (bf.getBean("manager"));

        try {
            manager.run(args);
        }catch (Exception e) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    public void run(String[] args) throws Exception {

        long time0 = System.currentTimeMillis();

        log.info(getVersion());
        log.info("   "+dao.getConnectionInfo());

        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

        boolean qcAll = false;
        boolean qcAliases = false;
        boolean qcAnnotations = false;
        boolean qcDuplicateAlleles = false;
        boolean qcHgncIds = false;
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
                case "--duplicate_alleles":
                    qcDuplicateAlleles = true;
                    break;
                case "--annotations":
                    qcAnnotations = true;
                    break;
                case "--hgncId":
                    qcHgncIds = true;
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

        if( qcDuplicateAlleles || qcAll ) {
            qcAlleles();
        }

        if( qcHgncIds || qcAll ) {
            qcHgncIds();
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
        log.info("=== OK -- elapsed time "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    void qcAlleles() throws Exception {

        log.info("");
        List<GenomicElement[]> results = dao.getGeneAllelesWithSameSymbols();
        dumpDuplicateAlleles("GENE ALLELES WITH DUPLICATE SYMBOLS: ", results);

        results = dao.getGeneAllelesWithSameNames();
        dumpDuplicateAlleles("GENE ALLELES WITH DUPLICATE NAMES: ", results);
    }

    void dumpDuplicateAlleles(String title, List<GenomicElement[]> results) {
        String msg = title + results.size();
        log.info(msg);
        logDuplicateAlleles.info(msg);

        if( !results.isEmpty() ) {
            log.info("===");
            for (GenomicElement[] arr : results) {
                msg = "RGD1:"+arr[0].getRgdId()+" SYMBOL1=["+arr[0].getSymbol()+"]  NAME1=["+arr[0].getName()+"]";
                log.info(msg);
                logDuplicateAlleles.info(msg);

                msg = "RGD2:"+arr[1].getRgdId()+" SYMBOL2=["+arr[1].getSymbol()+"]  NAME2=["+arr[1].getName()+"]";
                log.info(msg);
                logDuplicateAlleles.info(msg);
            }
            log.info("===");
            logDuplicateAlleles.info("===");
        }
    }

    void qcHgncIds() throws Exception {

        log.info("");
        List<String> lines = dao.getDuplicateHgncIds();
        log.info("DUPLICATE HGNC IDS: " +lines.size());

        for( String line: lines ) {
            log.info("   "+line);
        }
    }

    void qcRedundantGeneAliases() throws Exception {

        Logger logDeletedAliases = LogManager.getLogger("deleted_aliases");

        List<Alias> aliases = dao.getRedundantGeneAliases();
        for( Alias a: aliases ) {
            logDeletedAliases.info(a.dump("|"));
        }

        int aliasesDeleted = dao.deleteAliases(aliases);
        log.info("");
        log.info("deleted "+aliasesDeleted+" redundant gene aliases (aliases that were the same as gene name or symbol)");
    }

    void qcAnnotationsWithMmoNotes() throws Exception {

        int issueCount = 0;
        issueCount += qcAnnotationsWithMmoNotes(SpeciesType.HUMAN);
        issueCount += qcAnnotationsWithMmoNotes(SpeciesType.MOUSE);
        issueCount += qcAnnotationsWithMmoNotes(SpeciesType.RAT);

        log.info("");
        log.info("CC manual annotations with problematic MMO notes: "+issueCount);
    }

    int qcAnnotationsWithMmoNotes(int speciesTypeKey) throws Exception {

        Logger logMMO = LogManager.getLogger("annots_with_MMO_issues");

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
            String newNotes = a.getNotes().replaceAll("[\\s]+", " ").trim();
            a.setNotes(newNotes);
            dao.updateAnnotation(a, oldNotes, newNotes);
        }
        log.info("");
        log.info("ANNOTATIONS WITH NEW LINES IN NOTES, FIXED: " +annots.size());
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

        log.info("");

        List<Annotation> ndAnnots = dao.getNDAnnotationsForRootTerm(ontologyId);
        log.info("QC ND annotations for "+ontologyId+" ontology (aspect "+aspect+") : "+ndAnnots.size());

        List<Annotation> ndAnnotsForDelete = new ArrayList<>();
        for( Annotation ndAnnot: ndAnnots ) {
            int rgdId = ndAnnot.getAnnotatedObjectRgdId();
            List<Annotation> manualAnnots = dao.getRgdManualAnnotations(rgdId, aspect);
            if( !manualAnnots.isEmpty() ) {
                log.info("  "+manualAnnots.size() + " RGD:"+rgdId);
                ndAnnotsForDelete.add(ndAnnot);
            }
        }

        if( ndAnnotsForDelete.isEmpty() ) {
            log.info("  all ND annotations for " + ontologyId + " ontology (aspect " + aspect + ") are valid");
        } else {
            dao.deleteNDAnnotations(ndAnnotsForDelete);
            log.info("  "+ ndAnnotsForDelete.size() + " ND annotations for " + ontologyId + " ontology (aspect " + aspect + ") have been deleted");
        }
    }

    void qcAnnotationsWithInactiveReferences() throws Exception {

        log.info("");
        List<Annotation> annots = dao.getAnnotationsWithInactiveReferences();
        for( Annotation a: annots ) {
            log.info("    "+a.dump("|"));
        }
        log.info("ANNOTATIONS WITH INACTIVE REF_RGD_IDS: " +annots.size());
    }

    /**
     * RS ontology must have 'RGD ID:' synonyms properly formatted in order to be properly associated with a strain object
     * @throws Exception
     */
    void qcRsOntology() throws Exception {

        List<TermSynonym> malformedRsSynonyms = dao.getMalformedRsSynonyms();
        log.info("");
        log.info("MALFORMED RS SYNONYMS: "+malformedRsSynonyms.size());
        if( !malformedRsSynonyms.isEmpty() ) {
            log.info("");
            for( TermSynonym ts: malformedRsSynonyms ) {
                log.info("TERM_ACC:"+ts.getTermAcc()+"  SYN: ["+ts.getName()+"]");
            }
        }
    }

    void qcSequences() throws Exception {

        log.info("");
        int count = dao.getCountOfOrphanedSequences();
        log.info("ORPHANED SEQUENCES: "+count);

        log.info("");
        List<Integer> rgdIds = dao.getRgdIdsWithMultipleSequences("uniprot_seq");
        if( rgdIds.isEmpty() ) {
            log.info("NO RGD IDS WITH MULTIPLE uniprot_seq SEQUENCES");
        } else {
            log.info(+rgdIds.size()+" RGD IDS WITH MULTIPLE uniprot_seq SEQUENCES");
            for( Integer rgdId: rgdIds ) {
                log.info("    "+rgdId);
            }
        }

        // TODO: display orphaned RefSeq Accs (nucleotide RefSeq acc xrefs without a mtahcing transcript object)
    }

    void qcActiveQtlsWithInactiveMarkers() throws Exception {
        log.info("");
        List<IntStringMapQuery.MapPair> list = dao.getActiveQtlsWithInactiveMarkers();
        log.info("ACTIVE QTLS WITH INACTIVE MARKERS: "+list.size());

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

        log.info("");

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
                    log.info("unexpected reference type: "+ref.getReferenceType()+" for QTL "+q.getSymbol()+", REF_RGD_ID:"+refRgdId);
                }
            }
        }
        log.info("ACTIVE QTLS WITH RELATED QTLS: "+qtlsWithRelatedQtlsTotal);
        log.info("  added reference associations for related qtls: "+insertedEntries.size());

        if( !insertedEntries.isEmpty() ) {
            logRelatedQtls.info("Added reference associations for related qtls: "+insertedEntries.size());
            for( String msg: insertedEntries ) {
                logRelatedQtls.info(msg);
            }
            logRelatedQtls.info("");
        }
    }

    void qcTranscripts() throws Exception {

        log.info("");

        int xdbIdsInserted = 0;

        for( int speciesTypeKey: SpeciesType.getSpeciesTypeKeys() ) {
            if( SpeciesType.isSearchable(speciesTypeKey) ) {

                List<IntStringMapQuery.MapPair> list = dao.getNcbiTranscriptsWithoutXdbIds(speciesTypeKey);
                if( !list.isEmpty() ) {
                    List<XdbId> xdbIds = new ArrayList<>(list.size());
                    for( IntStringMapQuery.MapPair pair: list ) {
                        XdbId xdbId = new XdbId();
                        xdbId.setAccId(pair.stringValue);
                        xdbId.setRgdId(pair.keyValue);
                        xdbId.setSrcPipeline("RGD");
                        xdbId.setXdbKey(XdbId.XDB_KEY_GENEBANKNU);
                        xdbId.setCreationDate(new Date());
                        xdbId.setModificationDate(new Date());
                        xdbIds.add(xdbId);
                    }

                    int inserted = dao.insertXdbIds(xdbIds);
                    log.info("  inserted NCBI nucleotide xdb ids for "+SpeciesType.getCommonName(speciesTypeKey)+" transcripts: "+inserted);
                    xdbIdsInserted += inserted;
                }
            }
        }

        log.info("INSERTED NCBI NUCLEOTIDE XDB IDS FOR TRANSCRIPTS: "+xdbIdsInserted);
    }



    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

