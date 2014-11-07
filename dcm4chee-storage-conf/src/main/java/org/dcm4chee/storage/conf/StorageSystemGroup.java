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

import java.util.List;
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
@ConfigClass(objectClass = "dcmStorageSystem", nodeName = "dcmStorageSystem")
public class StorageSystemGroup {


    @ConfigField(name = "dcmStorageSystemGroupID")
    private String groupID;

    @ConfigField(name = "storageSystems", mapKey = "dcmStorageSystemID")
    private Map<String, StorageSystem> storageSystems;

    @ConfigField(name = "dcmActiveStorageSystemID")
    private List<String> activeStorageSystemIDs;
 
    @ConfigField(name = "dcmNextStorageSystemID")
    private String nextStorageSystemID;

    @ConfigField(name = "dicomInstalled")
    private Boolean installed;

    @ConfigField(name = "archiver")
    private Archiver archiver;

    @ConfigField(name = "fileCache")
    private FileCache fileCache;

    private StorageDeviceExtension storageDeviceExtension;

    private int activeStorageSystemIndex;

    public Boolean getInstalled() {
        return installed;
    }

    public void setInstalled(Boolean installed) {
        this.installed = installed;
    }

    public boolean isInstalled() {
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

    public StorageSystem getStorageSystem(String storageSystemID) {
        return storageSystems.get(storageSystemID);
    }

    public StorageSystem getNextStorageSystem() {
        if (nextStorageSystemID == null)
            return null;

        return getStorageSystem(nextStorageSystemID);
    }

    public StorageSystem getActiveStorageSystem() {
        String storageSystemID = activeStorageSystemIDs.get(activeStorageSystemIndex);
        activeStorageSystemIndex = (activeStorageSystemIndex + 1) % activeStorageSystemIDs.size();
        return getStorageSystem(storageSystemID);
    }

    public void switchActiveStorageSystem(String activeStorageSystemID,
            String nextStorageSystemID) {
        int index = activeStorageSystemIDs.indexOf(activeStorageSystemID);
        if (index == -1)
            throw new IllegalArgumentException("No active storage system with ID "
                    + nextStorageSystemID);
        StorageSystem next = getStorageSystem(nextStorageSystemID);
        if (index == -1)
            throw new IllegalArgumentException("No storage system with ID "
                    + nextStorageSystemID);

        activeStorageSystemIDs.set(index, next.getStorageSystemID());
        this.nextStorageSystemID= next.getNextStorageSystemID();
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

    public void setFileCache(FileCache retrieveCache) {
        this.fileCache = retrieveCache;
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

