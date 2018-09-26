package edu.mcw.rgd;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

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
            manager.run();
        }catch (Exception e) {
            manager.log.error(e);
            throw e;
        }
    }

    public void run() throws Exception {

        int count = dao.getCountOfOrphanedSequenceRgdIds();
        System.out.println(" ORPHANED SEQUENCE RGD IDS: "+count);
        /*
        UPDATE rgd_ids r SET last_modified_date=SYSDATE,object_status='WITHDRAWN'
        WHERE r.object_key=9 AND object_status='ACTIVE' AND NOT EXISTS
                (SELECT 1 FROM sequences s WHERE s.rgd_id=r.rgd_id)
        */

        count = dao.getCountOfOrphanedSequences();
        System.out.println(" ORPHANED SEQUENCES: "+count);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

