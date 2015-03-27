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

package org.dcm4chee.storage.conf;

import java.io.Serializable;
import java.util.*;

import javax.enterprise.inject.Instance;

import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;
import org.dcm4che3.conf.core.api.LDAP;
import org.dcm4che3.net.Device;
import org.dcm4chee.storage.spi.ContainerProvider;
import org.dcm4chee.storage.spi.FileCacheProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@LDAP(objectClasses = "dcmStorageSystemGroup")
@ConfigurableClass
public class StorageSystemGroup implements Serializable{

    private static final long serialVersionUID = -8283568746257849173L;

    @ConfigurableProperty(name = "dcmStorageSystemGroupID")
    private String groupID;

    @LDAP(distinguishingField = "dcmStorageSystemID", noContainerNode = true)
    @ConfigurableProperty(name = "Storage Systems")
    private Map<String, StorageSystem> storageSystems;

    @ConfigurableProperty(name = "dcmActiveStorageSystemID")
    private String[] activeStorageSystemIDs = {};
 
    @ConfigurableProperty(name = "dcmNextStorageSystemID")
    private String nextStorageSystemID;

    @ConfigurableProperty(name = "dcmStorageFilePathFormat")
    private String storageFilePathFormat;
    
    @ConfigurableProperty(name = "dicomInstalled")
    private Boolean installed;

    @ConfigurableProperty(name = "Storage Container")
    private Container container;

    @ConfigurableProperty(name = "Storage File Cache")
    private FileCache fileCache;

    @ConfigurableProperty(name = "dcmDigestAlgorithm")
    private String digestAlgorithm;

    @ConfigurableProperty(name = "dcmCalculateCheckSumOnStore", defaultValue="false")
    private boolean calculateCheckSumOnStore;

    @ConfigurableProperty(name = "dcmCalculateCheckSumOnRetrieve", defaultValue="false")
    private boolean calculateCheckSumOnRetrieve;

    private StorageDeviceExtension storageDeviceExtension;

    private int activeStorageSystemIndex;

    public Boolean getInstalled() {
        return installed;
    }

    public void setInstalled(Boolean installed) {
        this.installed = installed;
    }

    public boolean installed() {
        Device device = storageDeviceExtension != null
                ? storageDeviceExtension.getDevice()
                : null;
        return device != null && device.isInstalled() 
                && (installed == null || installed.booleanValue());
    }

    public StorageDeviceExtension getStorageDeviceExtension() {
        return storageDeviceExtension;
    }

    public void setStorageDeviceExtension(
            StorageDeviceExtension storageDeviceExtension) {
        if (storageDeviceExtension != null && this.storageDeviceExtension != null)
            throw new IllegalStateException("Storage System Group "
                    + groupID
                    + " already owned by other Storage Device Extension");

        this.storageDeviceExtension = storageDeviceExtension;
    }

    public Map<String, StorageSystem> getStorageSystems() {
        return storageSystems;
    }

    public void setStorageSystems(Map<String, StorageSystem> storageSystems) {
        this.storageSystems = storageSystems;
        for (StorageSystem storageSystem : storageSystems.values()) {
            storageSystem.setStorageSystemGroup(this);
        }
    }

    public StorageSystem getStorageSystem(String storageSystemID) {
        if (storageSystems == null)
            return null;

        return storageSystems.get(storageSystemID);
    }

    public StorageSystem addStorageSystem(StorageSystem storageSystem) {
        if (storageSystems == null)
            storageSystems = new TreeMap<String,StorageSystem>();

        storageSystem.setStorageSystemGroup(this);
        StorageSystem prev = storageSystems.put(storageSystem.getStorageSystemID(),
                storageSystem);
        if (prev != null)
            prev.setStorageSystemGroup(null);
        return prev;
    }

    public StorageSystem removeStorageSystem(String storageSystemID) {
        if (storageSystems == null)
            return null;

        StorageSystem system = storageSystems.remove(storageSystemID);
        if (system == null)
            return null;

        return system;
    }

    public Collection<String> getStorageSystemIDs() {
        if (storageSystems == null)
            return Collections.emptySet();

        return storageSystems.keySet();
    }

    public String getStorageFilePathFormat() {
		return storageFilePathFormat;
	}

	public void setStorageFilePathFormat(String storageFilePathFormat) {
		this.storageFilePathFormat = storageFilePathFormat;
	}

	public String getNextStorageSystemID() {
        return nextStorageSystemID;
    }

    public void setNextStorageSystemID(String nextStorageSystemID) {
        this.nextStorageSystemID = nextStorageSystemID;
    }

    public StorageSystem getNextStorageSystem() {
        return getStorageSystem(nextStorageSystemID);
    }

    public String[] getActiveStorageSystemIDs() {
        return activeStorageSystemIDs;
    }

    public void setActiveStorageSystemIDs(String... activeStorageSystemIDs) {
        this.activeStorageSystemIDs = activeStorageSystemIDs;
    }

    public String getDigestAlgorithm() {
		return digestAlgorithm;
	}

	public void setDigestAlgorithm(String digestAlgorithm) {
		this.digestAlgorithm = digestAlgorithm;
	}

	public synchronized void activate(StorageSystem storageSystem, boolean setNextStorageSystemID) {
        int length = activeStorageSystemIDs.length;
        activeStorageSystemIDs = Arrays.copyOf(activeStorageSystemIDs, length+1);
        activeStorageSystemIDs[length] = storageSystem.getStorageSystemID();
        if (setNextStorageSystemID)
            setNextStorageSystemID(storageSystem.getNextStorageSystemID());
    }

    public synchronized void deactivate(StorageSystem storageSystem) {
        String systemID = storageSystem.getStorageSystemID();
        for (int i = 0; i < activeStorageSystemIDs.length; i++) {
            if (activeStorageSystemIDs[i].equals(systemID)) {
                String[] dest = new String[activeStorageSystemIDs.length-1];
                System.arraycopy(activeStorageSystemIDs, 0, dest, 0, i);
                System.arraycopy(activeStorageSystemIDs, i+1, dest, i, dest.length-i);
                activeStorageSystemIDs = dest;
                if (i <  activeStorageSystemIndex)
                    activeStorageSystemIndex--;
                return;
            }
        }
    }

    public synchronized StorageSystem nextActiveStorageSystem() {
        if (activeStorageSystemIDs.length == 0)
            return null;

        activeStorageSystemIndex %= activeStorageSystemIDs.length;
        String storageSystemID = activeStorageSystemIDs[activeStorageSystemIndex];
        activeStorageSystemIndex++;
        return getStorageSystem(storageSystemID);
    }

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
    }

    public ContainerProvider getContainerProvider(
            Instance<ContainerProvider> instances) {
        return container != null
                ? container.getContainerProvider(instances)
                : null;
    }

    public FileCacheProvider getFileCacheProvider(
            Instance<FileCacheProvider> instances) {
        return fileCache != null
                ? fileCache.getFileCacheProvider(instances)
                : null;
    }

    public boolean isCalculateCheckSumOnStore() {
        return calculateCheckSumOnStore;
    }

    public void setCalculateCheckSumOnStore(boolean calculateCheckSum) {
        this.calculateCheckSumOnStore = calculateCheckSum;
    }

    public boolean isCalculateCheckSumOnRetrieve() {
        return calculateCheckSumOnRetrieve;
    }

    public void setCalculateCheckSumOnRetrieve(boolean calculateCheckSum) {
        this.calculateCheckSumOnRetrieve = calculateCheckSum;
    }

    @Override
    public String toString() {
        return "StorageSystemGroup[id=" + groupID
                + ", activeStorageSystems=" 
                + Arrays.toString(activeStorageSystemIDs) + "]";
    }
}

