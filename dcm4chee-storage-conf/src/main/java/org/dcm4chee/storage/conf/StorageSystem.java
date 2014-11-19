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

import javax.enterprise.inject.Instance;

import org.dcm4che3.conf.api.generic.ConfigClass;
import org.dcm4che3.conf.api.generic.ConfigField;
import org.dcm4chee.storage.spi.ArchiverProvider;
import org.dcm4chee.storage.spi.FileCacheProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@ConfigClass(objectClass = "dcmStorageSystem", nodeName = "dcmStorageSystem")
public class StorageSystem {

    @ConfigField(name = "dcmProviderName")
    private String providerName;

    @ConfigField(name = "dcmStorageSystemID")
    private String storageSystemID;

    @ConfigField(name = "dcmStorageSystemPath")
    private String storageSystemPath;

    @ConfigField(name = "dcmStorageSystemStatus")
    private StorageSystemStatus storageSystemStatus;

    @ConfigField(name = "dcmNextStorageSystemID")
    private String nextStorageSystemID;

    @ConfigField(name = "dcmStorageMinFreeSpace")
    private String minFreeSpace;

    @ConfigField(name = "dcmStorageReadOnly", def = "false")
    private boolean readOnly;

    @ConfigField(name = "dcmStorageAccessTime", def = "0")
    private int storageAccessTime;

    @ConfigField(name = "dcmStorageMountCheckFile")
    private String mountCheckFile;

    @ConfigField(name = "dcmStorageSystemURI")
    private String storageSystemURI;

    @ConfigField(name = "dcmStorageSystemAPI")
    private String storageSystemAPI;

    @ConfigField(name = "dcmStorageSystemIdentity")
    private String storageSystemIdentity;

    @ConfigField(name = "dcmStorageSystemCredential")
    private String storageSystemCredential;

    @ConfigField(name = "dcmStorageSystemContainer")
    private String storageSystemContainer;

    @ConfigField(name = "dcmStorageSystemMaxConnections", def = "5")
    private int maxConnections;

    @ConfigField(name = "dcmStorageSystemConnectionTimeout", def = "60000")
    private long connectionTimeout;

    @ConfigField(name = "dcmStorageSystemSocketTimeout", def = "60000")
    private long socketTimeout;

    @ConfigField(name = "dcmStorageSystemMulipartUpload", def = "true")
    private boolean multipartUpload;

    @ConfigField(name = "dcmStorageSystemMultipartChunkSize", def = "10MB")
    private String multipartChunkSize;

    @ConfigField(name = "dicomInstalled")
    private Boolean installed;

    private StorageSystemGroup storageSystemGroup;
    private long minFreeSpaceInBytes = -1L;
    private StorageSystemProvider storageSystemProvider;
    private long multipartChunkSizeInBytes = -1L;

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getStorageSystemID() {
        return storageSystemID;
    }

    public void setStorageSystemID(String storageSystemID) {
        this.storageSystemID = storageSystemID;
    }

    public String getNextStorageSystemID() {
        return nextStorageSystemID;
    }

    public void setNextStorageSystemID(String nextStorageSystemID) {
        this.nextStorageSystemID = nextStorageSystemID;
    }

    public StorageSystem getNextStorageSystem() {
        return storageSystemGroup.getStorageSystem(nextStorageSystemID);
    }

    public int getStorageAccessTime() {
        return storageAccessTime;
    }

    public void setStorageAccessTime(int storageAccessTime) {
        this.storageAccessTime = storageAccessTime;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public StorageSystemStatus getStorageSystemStatus() {
        return storageSystemStatus;
    }

    public void setStorageSystemStatus(StorageSystemStatus storageSystemStatus) {
        this.storageSystemStatus = storageSystemStatus;
    }

    public String getStorageSystemPath() {
        return storageSystemPath;
    }

    public void setStorageSystemPath(String storageSystemPath) {
        this.storageSystemPath = storageSystemPath;
    }

    public StorageSystemGroup getStorageSystemGroup() {
        return storageSystemGroup;
    }

    public void setStorageSystemGroup(StorageSystemGroup storageSystemGroup) {
        if (storageSystemGroup != null && this.storageSystemGroup != null)
            throw new IllegalStateException("Storage System "
                    + storageSystemID
                    + " already owned by Storage System Group "
                    + storageSystemGroup.getGroupID());
        this.storageSystemGroup = storageSystemGroup;
    }

    public String getMinFreeSpace() {
        return minFreeSpace;
    }

    public void setMinFreeSpace(String minFreeSpace) {
        this.minFreeSpaceInBytes = minFreeSpace != null ? Utils
                .parseByteSize(minFreeSpace) : -1L;
        this.minFreeSpace = minFreeSpace;
    }

    public long getMinFreeSpaceInBytes() {
        return minFreeSpaceInBytes;
    }

    public String getMountCheckFile() {
        return mountCheckFile;
    }

    public void setMountCheckFile(String mountCheckFile) {
        this.mountCheckFile = mountCheckFile;
    }

    public String getStorageSystemURI() {
        return storageSystemURI;
    }

    public void setStorageSystemURI(String storageSystemURI) {
        this.storageSystemURI = storageSystemURI;
    }

    public String getStorageSystemAPI() {
        return storageSystemAPI;
    }

    public void setStorageSystemAPI(String storageSystemAPI) {
        this.storageSystemAPI = storageSystemAPI;
    }

    public String getStorageSystemIdentity() {
        return this.storageSystemIdentity;
    }

    public void setStorageSystemIdentity(String storageSystemIdentity) {
        this.storageSystemIdentity = storageSystemIdentity;
    }

    public String getStorageSystemCredential() {
        return storageSystemCredential;
    }

    public void setStorageSystemCredential(String storageSystemCredential) {
        this.storageSystemCredential = storageSystemCredential;
    }

    public String getStorageSystemContainer() {
        return storageSystemContainer;
    }

    public void setStorageSystemContainer(String storageSystemContainer) {
        this.storageSystemContainer = storageSystemContainer;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnection(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectTimeout) {
        this.connectionTimeout = connectTimeout;
    }

    public long getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(long socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public boolean isMultipartUpload() {
        return multipartUpload;
    }

    public void setMultipartUpload(boolean multipartUpload) {
        this.multipartUpload = multipartUpload;
    }

    public String getMultipartChunkSize() {
        return multipartChunkSize;
    }

    public void setMultipartChunkSize(String multipartChunkSize) {
        this.multipartChunkSizeInBytes = multipartChunkSize != null ? Utils
                .parseByteSize(multipartChunkSize) : -1L;
        this.multipartChunkSize = multipartChunkSize;
    }

    public long getMultipartChunkSizeInBytes() {
        return multipartChunkSizeInBytes;
    }

    public Boolean getInstalled() {
        return installed;
    }

    public void setInstalled(Boolean installed) {
        this.installed = installed;
    }

    public boolean installed() {
        return storageSystemGroup != null && storageSystemGroup.installed()
                && (installed == null || installed.booleanValue());
    }

    public StorageSystemProvider getStorageSystemProvider(
            Instance<StorageSystemProvider> instances) {
        if (storageSystemProvider == null) {
            storageSystemProvider = instances.select(
                    new NamedQualifier(providerName)).get();
            storageSystemProvider.init(this);
        }
        return storageSystemProvider;
    }

    public ArchiverProvider getArchiverProvider(
            Instance<ArchiverProvider> instances) {
        return storageSystemGroup.getArchiverProvider(instances);
    }

    public FileCacheProvider getFileCacheProvider(
            Instance<FileCacheProvider> instances) {
        return storageSystemGroup.getFileCacheProvider(instances);
    }

    public StorageDeviceExtension getStorageDeviceExtension() {
        return storageSystemGroup.getStorageDeviceExtension();
    }
}
