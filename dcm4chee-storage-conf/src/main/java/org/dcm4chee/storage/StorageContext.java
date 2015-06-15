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

package org.dcm4chee.storage;

import java.security.MessageDigest;
import java.util.HashMap;

import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.spi.FileCacheProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.dcm4chee.storage.spi.ContainerProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
public class StorageContext {

    private StorageSystemProvider storageSystemProvider;
    private ContainerProvider containerProvider;
    private FileCacheProvider fileCacheProvider;
    private StorageSystem storageSystem;
    private long fileSize;
    private MessageDigest digest;
    private HashMap<String,Object> properties = new HashMap<String,Object>();

    public StorageSystemProvider getStorageSystemProvider() {
        return storageSystemProvider;
    }

    public void setStorageSystemProvider(StorageSystemProvider storageSystemProvider) {
        this.storageSystemProvider = storageSystemProvider;
    }

    public ContainerProvider getContainerProvider() {
        return containerProvider;
    }

    public void setContainerProvider(ContainerProvider containerProvider) {
        this.containerProvider = containerProvider;
    }

    public FileCacheProvider getFileCacheProvider() {
        return fileCacheProvider;
    }

    public void setFileCacheProvider(FileCacheProvider fileCacheProvider) {
        this.fileCacheProvider = fileCacheProvider;
    }

    public StorageSystem getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(StorageSystem storageSystem) {
        this.storageSystem = storageSystem;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public MessageDigest getDigest() {
        return digest;
    }

    public void setDigest(MessageDigest digest) {
        this.digest = digest;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

}
