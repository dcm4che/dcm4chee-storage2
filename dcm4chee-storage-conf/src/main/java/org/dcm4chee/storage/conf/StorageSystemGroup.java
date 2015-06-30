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

    private static final String AFFINITY_GROUP_ID_PROPERTY = "org.dcm4chee.storage.affinityGroupID";

    @ConfigurableProperty(name = "dcmStorageSystemGroupID")
    private String groupID;

    @LDAP(distinguishingField = "dcmStorageSystemID", noContainerNode = true)
    @ConfigurableProperty(name = "Storage Systems")
    private Map<String, StorageSystem> storageSystems;

    //deletion rule
    @ConfigurableProperty(name = "dcmArchivedOnGroups")
    private String[] archivedOnGroups;

    @ConfigurableProperty(name = "dcmArchivedOnExternalSystems")
    private String[] archivedOnExternalSystems;

    @ConfigurableProperty(name = "dcmMinTimeStudyNotAccessed")
    private int minTimeStudyNotAccessed;
    
    @ConfigurableProperty(name = "dcmMinTimeStudyNotAccessedUnit")
    private String minTimeStudyNotAccessedUnit;

    @ConfigurableProperty(name = "dcmDeleteAsMuchAsPossible")
    private boolean deleteAsMuchAsPossible;

    @ConfigurableProperty(name = "dcmDeletionThreshold")
    private String deletionThreshold;

    @ConfigurableProperty(name = "dcmArchivedAnyWhere")
    private boolean archivedAnyWhere;
    //end deletion rule

    @ConfigurableProperty(name = "dcmActiveStorageSystemID")
    private String[] activeStorageSystemIDs = {};

    @ConfigurableProperty(name = "dcmNextStorageSystemID")
    private String nextStorageSystemID;

    @ConfigurableProperty(name = "dcmStorageParallelism", defaultValue = "1")
    private int parallelism = 1;

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

    @ConfigurableProperty(name = "dcmRetrieveAET")
    private String[] retrieveAETs = {};

    @ConfigurableProperty(name = "dcmCalculateCheckSumOnStore", defaultValue="false")
    private boolean calculateCheckSumOnStore;

    @ConfigurableProperty(name = "dcmCalculateCheckSumOnRetrieve", defaultValue="false")
    private boolean calculateCheckSumOnRetrieve;

    @ConfigurableProperty(name = "dcmBaseStorageAccessTime", defaultValue = "0")
    private int baseStorageAccessTime;

    @ConfigurableProperty(name = "dcmSpoolStorageGroup")
    private String spoolStorageGroup;

    @ConfigurableProperty(name = "dcmStorageSystemGroupLabel")
    private String storageSystemGroupLabel;


    @LDAP(
            distinguishingField = "dcmStorageAffinityGroupID",
            mapValueAttribute = "dcmStorageAccessTimeOffset",
            mapEntryObjectClass= "dcmStorageAccessTimeOffsetEntry"
    )
    @ConfigurableProperty(name = "StorageAccessTimeOffsetMap")
    private final Map<String, String> storageAccessTimeOffsetMap = new TreeMap<String, String>(
            (String.CASE_INSENSITIVE_ORDER));

    @ConfigurableProperty(name = "description")
    private String description;

    public String getStorageSystemGroupLabel() {
        return storageSystemGroupLabel;
    }

    public void setStorageSystemGroupLabel(String storageSystemGroupLabel) {
        this.storageSystemGroupLabel = storageSystemGroupLabel;
    }

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

        if (nextStorageSystemID == null)
            nextStorageSystemID = storageSystem.getStorageSystemID();

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

	public boolean isArchivedAnyWhere() {
        return archivedAnyWhere;
    }

    public void setArchivedAnyWhere(boolean archivedAnyWhere) {
        this.archivedAnyWhere = archivedAnyWhere;
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
        if (nextStorageSystemID == null)
            return null;

        return getStorageSystem(nextStorageSystemID);
    }

    public String[] getActiveStorageSystemIDs() {
        return activeStorageSystemIDs;
    }

    public void setActiveStorageSystemIDs(String... activeStorageSystemIDs) {
        this.activeStorageSystemIDs = activeStorageSystemIDs;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public String getDigestAlgorithm() {
		return digestAlgorithm;
	}

	public void setDigestAlgorithm(String digestAlgorithm) {
		this.digestAlgorithm = digestAlgorithm;
	}

	public synchronized void activate(StorageSystem storageSystem, boolean setNextStorageSystemID) {
	    if (!isActive(storageSystem)) {
            int length = activeStorageSystemIDs.length;
            activeStorageSystemIDs = Arrays.copyOf(activeStorageSystemIDs, length+1);
            activeStorageSystemIDs[length] = storageSystem.getStorageSystemID();
            if (setNextStorageSystemID)
                setNextStorageSystemID(storageSystem.getNextStorageSystemID());
	    }
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

    public boolean isActive(StorageSystem storageSystem) {
        return Arrays.asList(activeStorageSystemIDs).contains(
                storageSystem.getStorageSystemID());
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

    public String[] getRetrieveAETs() {
        return retrieveAETs;
    }

    public void setRetrieveAETs(String[] retrieveAETs) {
        this.retrieveAETs = retrieveAETs;
    }

    public int getBaseStorageAccessTime() {
        return baseStorageAccessTime;
    }

    public void setBaseStorageAccessTime(int baseStorageAccessTime) {
        this.baseStorageAccessTime = baseStorageAccessTime;
    }

    public Map<String, String> getStorageAccessTimeOffsetMap() {
        return storageAccessTimeOffsetMap;
    }

    public void setStorageAccessTimeOffsetMap(
            Map<String, String> storageAccessTimeOffsetMap) {
        this.storageAccessTimeOffsetMap.clear();
        if (storageAccessTimeOffsetMap != null)
            this.storageAccessTimeOffsetMap.putAll(storageAccessTimeOffsetMap);
    }

    public int getStorageAccessTimeOffset() {
        String id = System.getProperty(AFFINITY_GROUP_ID_PROPERTY);
        if (id == null)
            return 0;
        String offset = storageAccessTimeOffsetMap.get(id);
        return offset != null ? Integer.parseInt(offset) : 0;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpoolStorageGroup() {
        return spoolStorageGroup;
    }

    public void setSpoolStorageGroup(String spoolStorageGroup) {
        this.spoolStorageGroup = spoolStorageGroup;
    }

    public String[] getArchivedOnGroups() {
        return archivedOnGroups;
    }

    public void setArchivedOnGroups(String[] archivedOnGroups) {
        this.archivedOnGroups = archivedOnGroups;
    }

    public String[] getArchivedOnExternalSystems() {
        return archivedOnExternalSystems;
    }

    public void setArchivedOnExternalSystems(String[] archivedOnExternalSystems) {
        this.archivedOnExternalSystems = archivedOnExternalSystems;
    }

    public int getMinTimeStudyNotAccessed() {
        return minTimeStudyNotAccessed;
    }

    public void setMinTimeStudyNotAccessed(int minTimeStudyNotAccessed) {
        this.minTimeStudyNotAccessed = minTimeStudyNotAccessed;
    }

    public String getMinTimeStudyNotAccessedUnit() {
        return minTimeStudyNotAccessedUnit;
    }

    public void setMinTimeStudyNotAccessedUnit(String minTimeStudyNotAccessedUnit) {
        this.minTimeStudyNotAccessedUnit = minTimeStudyNotAccessedUnit;
    }

    public boolean isDeleteAsMuchAsPossible() {
        return deleteAsMuchAsPossible;
    }

    public void setDeleteAsMuchAsPossible(boolean deleteAsMuchAsPossible) {
        this.deleteAsMuchAsPossible = deleteAsMuchAsPossible;
    }

    public String getDeletionThreshold() {
        return deletionThreshold;
    }

    public void setDeletionThreshold(String deletionThreshold) {
        this.deletionThreshold = deletionThreshold;
    }

    @Override
    public String toString() {
        return "StorageSystemGroup[id=" + groupID
                + ", activeStorageSystems=" 
                + Arrays.toString(activeStorageSystemIDs) + "]";
    }
}

