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
 * Portions created by the Initial Developer are Copyright (C) 2011-2014
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

package org.dcm4chee.storage.service.impl;

import static java.lang.String.format;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.net.Device;
import org.dcm4chee.storage.StorageDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * CDI Producer for storage configuration device that is used by all storage providers.
 * 
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
@ApplicationScoped
public class StorageDeviceProducer {
    private static final Logger LOG = LoggerFactory.getLogger(StorageDeviceProducer.class);
            
    private static final String STORAGE_DEVICE_NAME_PROPERTY = "org.dcm4chee.storage.deviceName";
    
    private static final String ARCHIVE_DEVICE_NAME_PROPERTY = "org.dcm4chee.archive.deviceName";
    private static final String DEF_ARCHIVE_DEVICE_NAME = "dcm4chee-arc";
    
    private static final String ARR_DEVICE_NAME_PROPERTY = "org.dcm4chee.arr.deviceName";
    private static final String DEF_ARR_DEVICE_NAME = "dcm4chee-arr";

    @Inject
    private DicomConfiguration conf;

    private Device device;
    
    private ExecutorService executor;
    private ScheduledExecutorService scheduledExecutor;

    @PostConstruct
    private void init() {
        try {
            device = findDevice();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Produces @StorageDevice
    public Device getDevice() {
        return device;
    }
    
    public void dispose(@Disposes @StorageDevice Device device) {
        LOG.info("Disposing storage configuration device instance " + device.getDeviceName());
        executor.shutdown();
        scheduledExecutor.shutdown();
    }

    private Device findDevice() throws ConfigurationException {
        // try to lookup by explicit set properties
        String storageDeviceName = System.getProperty(STORAGE_DEVICE_NAME_PROPERTY);
        if(storageDeviceName == null ) {
          storageDeviceName = System.getProperty(ARCHIVE_DEVICE_NAME_PROPERTY);
          if(storageDeviceName == null) {
              storageDeviceName = System.getProperty(ARR_DEVICE_NAME_PROPERTY);
          }
        }
        
        if(storageDeviceName != null) {
            device = conf.findDevice(storageDeviceName);
            if (device == null) {
                throw new ConfigurationException(format("Storage device '%s' does not exist in the configuration", storageDeviceName));
            }
        }
        // if no set property found try fallback to default archive or ARR device  
        else {
            device = conf.findDevice(DEF_ARCHIVE_DEVICE_NAME);
            if(device == null ){
                device = conf.findDevice(DEF_ARR_DEVICE_NAME);
                if(device == null) {
                    throw new ConfigurationException(format("No suitable Storage device could be found in the configuration"));
                }
            }
        }
        
        setupStorageDeviceThreadPool(device);
        
        LOG.info(format("Using device '%s' as storage configuration device", device.getDeviceName()));
        return device;
    }
    
    private void setupStorageDeviceThreadPool(Device device) {
        executor = Executors.newCachedThreadPool();
        device.setExecutor(executor);
        scheduledExecutor = Executors.newScheduledThreadPool(3);
        device.setScheduledExecutor(scheduledExecutor);
    }

}
