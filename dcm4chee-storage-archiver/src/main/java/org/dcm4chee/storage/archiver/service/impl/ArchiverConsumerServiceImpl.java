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
 * Portions created by the Initial Developer are Copyright (C) 2012-2014
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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.dcm4chee.storage.archiver.service.ArchiverConsumerService;
import org.dcm4chee.storage.archiver.service.ArchiverContext;
import org.dcm4chee.storage.archiver.service.ArchiverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 *
 */
@ApplicationScoped
public class ArchiverConsumerServiceImpl  implements ArchiverConsumerService{

    private static final Logger LOG = LoggerFactory.getLogger(ArchiverConsumerService.class);

    private Map<String, ConsumerQueueTuple> consumers;

    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory connFactory;

    @Inject
    private ArchiverService archiverService;

    @PostConstruct
    public void init() {
        consumers = new HashMap<String, ConsumerQueueTuple>();
    }

    
    @Override
    public MessageConsumer findOrCreateConsumer(final ArchiverContext ctx, final int retries) {

        String consumerID = ctx.getDestinationID();
        boolean externalArchiving = ctx.isStoreAndRemember();

        try {
            if (!consumers.containsKey(consumerID)) {
                // create
                Connection connection = connFactory.createConnection();
                Session session = connection.createSession(false,
                        Session.AUTO_ACKNOWLEDGE);
                TemporaryQueue queue = createConsumerQueue(consumerID, session);
                MessageListener listener = new MessageListener() {
                    
                    @Override
                    public void onMessage(Message message) {
                        archiverService.store(ctx, retries);
                    }
                };
                MessageConsumer consumer = session.createConsumer(queue);
                consumer.setMessageListener(listener);
                consumers.put(consumerID, new ConsumerQueueTuple(queue,
                        consumer, connection, session));
                connection.start();
            }
        } catch (JMSException e) {
            LOG.error("Unable to find or create dynamic "
                    + "consumer for ID {}", consumerID);
            return null;
        }
        return consumers.get(consumerID).getConsumer();
    }

    @Override
    public void scheduleMessageToTempQueue(Message msg, String consumerID) {
        Session session = null;
        try {
                 session = consumers.get(consumerID).getSession();
                MessageProducer producer = session
                        .createProducer(consumers.get(consumerID).getQueue());
                producer.send(msg);

        } catch (JMSException e) {
            LOG.error("unable to schedule message to tempQueue "
                    + "for consumer {} - reason {}", consumerID, e );
            try {
                session.commit();
                session.close();
                consumers.get(consumerID).getConnection().close();
                consumers.get(consumerID).getQueue().delete();
                consumers.remove(consumerID);
            } catch (JMSException e1) {
                LOG.error("Unable to close session or connection for temporary consumer {}", consumerID);
                try {
                    consumers.get(consumerID).getQueue().delete();
                } catch (JMSException e2) {
                    LOG.error("Unable to delete temporary queue {}", consumerID);
                }
                consumers.remove(consumerID);
            }
            throw new RuntimeException();
        }
    }

    @Override
    public Destination findConsumerQueue(String consumerID) {
        return consumers.get(consumerID).getQueue();
    }
    

    private TemporaryQueue createConsumerQueue(String consumerID,
            Session session) throws JMSException {
        return session.createTemporaryQueue();
    }

    private MessageListener getExternalArchivingMessageListener(ArchiverContext ctx, int retries) {
        // TODO Auto-generated method stub
        return null;
    }

    private MessageListener getLocalArchivingMessageListener(final ArchiverContext ctx, final int retries) {
        return new MessageListener() {
            
            @Override
            public void onMessage(Message message) {
                archiverService.store(ctx, retries);
            }
        };
    }

    class ConsumerQueueTuple {

        private TemporaryQueue queue;

        private MessageConsumer consumer;

        private Session session;
        
        private Connection connection;

        public ConsumerQueueTuple(TemporaryQueue queue, MessageConsumer consumer,
                Connection connection, Session session) {
            super();
            this.queue = queue;
            this.consumer = consumer;
            this.connection = connection;
            this.session = session;
        }

        public TemporaryQueue getQueue() {
            return queue;
        }

        public MessageConsumer getConsumer() {
            return consumer;
        }

        public Session getSession() {
            return session;
        }

        public Connection getConnection() {
            return connection;
        }

    }
}
