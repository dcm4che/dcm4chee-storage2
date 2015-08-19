//
/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.storage.archiver.service.impl;

import java.io.IOException;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.dcm4chee.storage.archiver.service.ArchiverContext;
import org.dcm4chee.storage.archiver.service.ArchivingQueueProvider;
import org.dcm4chee.storage.archiver.service.ExternalDeviceArchiverContext;
import org.dcm4chee.storage.archiver.service.ExternalDeviceArchiverContext.ARCHIVING_PROTOCOL;
import org.dcm4chee.storage.archiver.service.StorageSystemArchiverContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
@ApplicationScoped
public class ArchivingQueueSchedulerImpl implements ArchivingQueueScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ArchivingQueueSchedulerImpl.class);
    
    private static final String ARCHIVING_MSG_TYPE_PROP = "archiving_msg_type";
    
    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory connFactory;

    @Inject
    private ArchivingQueueProvider queueProvider;

    @Override
    public StorageSystemArchiverContext createStorageSystemArchiverContext(String storageSystemGroupID, String name) {
        return new StorageSystemArchiverContext(name, storageSystemGroupID);
    }
    
    @Override
    public ExternalDeviceArchiverContext createExternalDeviceArchiverContext(String externalDeviceName, ARCHIVING_PROTOCOL archivingProtocol) {
        return new ExternalDeviceArchiverContext(externalDeviceName, archivingProtocol);
    }

    @Override
    public void scheduleStore(ArchiverContext context) throws IOException {
        scheduleStore(context, 0, 0);
    }

    @Override
    public void scheduleStore(ArchiverContext context, int retries, long delay) {
        try {
            Connection conn = connFactory.createConnection();
            try {
                Session session = conn.createSession(false,
                        Session.AUTO_ACKNOWLEDGE);
                Queue archivingQueue = queueProvider.getQueue(context);
                if(archivingQueue != null) {
                    MessageProducer producer = session.createProducer(archivingQueue);
                    ObjectMessage msg = session.createObjectMessage(context);
                    msg.setIntProperty("Retries", retries);
                    // set message type -> Receiving MDBs might filter messages based on type
                    msg.setStringProperty(ARCHIVING_MSG_TYPE_PROP, context.getClass().getName());
                    if (delay > 0) {
                        msg.setLongProperty("_HQ_SCHED_DELIVERY", System.currentTimeMillis() + delay);
                    }
                    producer.send(msg);
                }
            } finally {
                conn.close();
            }
        } catch (JMSException e) {
            throw new RuntimeException("Error while scheduling archiving JMS message", e);
        }
    }
   
}
