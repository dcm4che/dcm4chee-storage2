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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.inject.Instance;

import org.dcm4che3.conf.api.generic.ConfigClass;
import org.dcm4che3.conf.api.generic.ConfigField;
import org.dcm4che3.net.Device;
import org.dcm4chee.storage.spi.ArchiverProvider;
import org.dcm4chee.storage.spi.FileCacheProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@ConfigClass(objectClass = "dcmStorageSystemGroup")
public class StorageSystemGroup {

    @ConfigField(name = "dcmStorageSystemGroupID")
    private String groupID;

    @ConfigField(name = "Storage Systems", mapKey = "dcmStorageSystemID")
    private Map<String, StorageSystem> storageSystems;

    @ConfigField(name = "dcmActiveStorageSystemID")
    private String[] activeStorageSystemIDs = {};
 
    @ConfigField(name = "dcmNextStorageSystemID")
    private String nextStorageSystemID;

    @ConfigField(name = "dicomInstalled")
    private Boolean installed;

    @ConfigField(name = "Storage Archiver", failIfNotPresent = false)
    private Archiver archiver;

    @ConfigField(name = "Storage File Cache", failIfNotPresent = false)
    private FileCache fileCache;

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
        this.storageDeviceExtension = storageDeviceExtension;
    }

    public Map<String, StorageSystem> getStorageSystems() {
        return storageSystems;
    }

    public void setStorageSystems(Map<String, StorageSystem> storageSystems) {
        this.storageSystems = storageSystems;
    }

    public StorageSystem getStorageSystem(String storageSystemID) {
        if (storageSystems == null)
            return null;

        return storageSystems.get(storageSystemID);
    }

    public StorageSystem addStorageSystem(StorageSystem storageSystem) {
        if (storageSystems == null)
            storageSystems = new HashMap<String,StorageSystem>();

        return storageSystems.put(storageSystem.getStorageSystemID(),
                storageSystem);
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

    public void setActiveStorageSystemIDs(String[] activeStorageSystemIDs) {
        this.activeStorageSystemIDs = activeStorageSystemIDs;
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

    public Archiver getArchiver() {
        return archiver;
    }

    public void setArchiver(Archiver archiver) {
        this.archiver = archiver;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
    }

    public ArchiverProvider getArchiverProvider(
            Instance<ArchiverProvider> instances) {
        return archiver != null
                ? archiver.getArchiverProvider(instances)
                : null;
    }

    public FileCacheProvider getFileCacheProvider(
            Instance<FileCacheProvider> instances) {
        return fileCache != null
                ? fileCache.getFileCacheProvider(instances)
                : null;
    }

}

