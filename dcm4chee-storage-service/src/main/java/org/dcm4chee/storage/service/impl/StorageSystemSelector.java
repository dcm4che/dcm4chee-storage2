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

package org.dcm4chee.storage.service.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.enterprise.inject.Instance;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulation of logic for selecting storage systems used in store-pipeline.
 * 
 * Selecting the storage systems has the possible side-effect of modifying the configuration. 
 * Due to the design of the DICOM configuration these changes must be applied on a separate (= writeable) device instance.
 * The selector does this by first performing the storage system selection and then in a second step
 * apply the changes to the configuration. 
 * 
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
public class StorageSystemSelector {
    private static final Logger LOG = LoggerFactory.getLogger(StorageSystemSelector.class);
    
    private final StorageSystemGroup group;
    private final Instance<StorageSystemProvider> storageSystemProviders;
    
    private final Map<String,StorageSystemStatus> storageSystem2newStatus = new HashMap<>();
    
    private int activeStorageSystemIndex;
    private String[] activeStorageSystemIDs;
    private String nextStorageSystemID;
    private boolean configChanged;
    
    public StorageSystemSelector(StorageSystemGroup group, Instance<StorageSystemProvider> storageSystemProviders) {
        this.group = group;
        this.storageSystemProviders = storageSystemProviders;

        activeStorageSystemIndex = group.getActiveStorageSystemIndex();
        activeStorageSystemIDs = group.getActiveStorageSystemIDs();
        nextStorageSystemID = group.getNextStorageSystemID();
    }

    public StorageSystem selectStorageSystem(long reserveSpace) {
        StorageSystem selected = nextActiveStorageSystem();
        while (selected != null && !checkMinFreeSpace(selected, reserveSpace)) {
            deactivate(selected);
            selected = nextActiveStorageSystem();
            configChanged = true;
        }

        StorageSystem system, start;
        start = system = getNextStorageSystem();
        int parallelism = group.getParallelism();
        while (system != null && activeStorageSystemIDs.length < parallelism) {
            if (!isActive(system) && checkMinFreeSpace(system, reserveSpace)) {
                activate(system, true);
                configChanged = true;
                if (selected == null) {
                    selected = nextActiveStorageSystem();
                }
            }
            if ((system = system.getNextStorageSystem()) == start)
                system = null;
        }

        return selected;
    }
    
    public boolean isConfigurationChanged() {
        return configChanged;
    }
    
    public void mergeDeviceChanges(Device device) {
        if(configChanged) {
            StorageDeviceExtension storageExtension = device.getDeviceExtension(StorageDeviceExtension.class);
            StorageSystemGroup modifyGroup = storageExtension.getStorageSystemGroup(group.getGroupID());
            modifyGroup.setActiveStorageSystemIDs(activeStorageSystemIDs);
            modifyGroup.setActiveStorageSystemIndex(activeStorageSystemIndex);
            modifyGroup.setNextStorageSystemID(nextStorageSystemID);
            
            for(Entry<String,StorageSystemStatus> entry : storageSystem2newStatus.entrySet()) {
                StorageSystem storageSystem = modifyGroup.getStorageSystem(entry.getKey());
                storageSystem.setStorageSystemStatus(entry.getValue());
            }
        }
    }
    
    private StorageSystem nextActiveStorageSystem() {
        if (activeStorageSystemIDs.length == 0)
            return null;

        activeStorageSystemIndex %= activeStorageSystemIDs.length;
        String storageSystemID = activeStorageSystemIDs[activeStorageSystemIndex];
        activeStorageSystemIndex++;
        return group.getStorageSystem(storageSystemID);
    }
    
    public void deactivate(StorageSystem storageSystem) {
        String systemID = storageSystem.getStorageSystemID();
        for (int i = 0; i < activeStorageSystemIDs.length; i++) {
            if (activeStorageSystemIDs[i].equals(systemID)) {
                String[] dest = new String[activeStorageSystemIDs.length - 1];
                System.arraycopy(activeStorageSystemIDs, 0, dest, 0, i);
                System.arraycopy(activeStorageSystemIDs, i + 1, dest, i, dest.length - i);
                activeStorageSystemIDs = dest;
                if (i < activeStorageSystemIndex) {
                    activeStorageSystemIndex--;
                }
                return;
            }
        }
    }
    
    public void activate(StorageSystem storageSystem, boolean setNextStorageSystemID) {
        if (!isActive(storageSystem)) {
            int length = activeStorageSystemIDs.length;
            activeStorageSystemIDs = Arrays.copyOf(activeStorageSystemIDs, length+1);
            activeStorageSystemIDs[length] = storageSystem.getStorageSystemID();
            if (setNextStorageSystemID) {
                nextStorageSystemID = storageSystem.getNextStorageSystemID();
            }
        }
    }
    
    private boolean isActive(StorageSystem storageSystem) {
        return Arrays.asList(activeStorageSystemIDs).contains(
                storageSystem.getStorageSystemID());
    }
    
    private StorageSystem getNextStorageSystem() {
        if (nextStorageSystemID == null)
            return null;

        return group.getStorageSystem(nextStorageSystemID);
    }
    
    public boolean checkMinFreeSpace(StorageSystem system, long reserveSpace) {
        if (!system.installed()) {
            return false;
        }
        if (system.isReadOnly()) {
            return false;
        }
        if (system.getStorageSystemStatus() != StorageSystemStatus.OK) {
            return false;
        }

        StorageSystemProvider provider = system.getStorageSystemProvider(storageSystemProviders);

        try {
            provider.checkWriteable();
            if (system.getMinFreeSpace() != null) {
                if(system.getMinFreeSpaceInBytes() == -1L)
                    system.setMinFreeSpaceInBytes(provider.getTotalSpace()*Integer.valueOf
                            (system.getMinFreeSpace().replace("%", ""))/100);
                if(provider.getUsableSpace() < system.getMinFreeSpaceInBytes() + reserveSpace) {
                    LOG.info("Update Status of {} to FULL", system);
                    storageSystem2newStatus.put(system.getStorageSystemID(), StorageSystemStatus.FULL);
                    configChanged = true;
                    return false;
                }
            }
        } catch (IOException e) {
            LOG.warn("Update Status of {} to NOT_ACCESSABLE caused by", system, e);
            storageSystem2newStatus.put(system.getStorageSystemID(), StorageSystemStatus.NOT_ACCESSABLE);
            configChanged = true;
            return false;
        }
        
        return true;
    }
}
