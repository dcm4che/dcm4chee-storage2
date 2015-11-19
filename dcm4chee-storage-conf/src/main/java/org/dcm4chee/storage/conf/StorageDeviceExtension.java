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

import java.util.*;

import org.dcm4che3.conf.api.extensions.ReconfiguringIterator;
import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;
import org.dcm4che3.conf.core.api.LDAP;
import org.dcm4che3.net.DeviceExtension;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@LDAP(objectClasses = "dcmStorageDeviceExtension")
@ConfigurableClass
public class StorageDeviceExtension extends DeviceExtension {

    private static final long serialVersionUID = -3081754725798580124L;

    public static final String AFFINITY_GROUP_ID_PROPERTY = "org.dcm4chee.storage.affinityGroupID";

    @ConfigurableProperty(type = ConfigurableProperty.ConfigurablePropertyType.OptimisticLockingHash)
    private String olockHash;

    @LDAP(distinguishingField = "dcmStorageSystemGroupID", noContainerNode = true)
    @ConfigurableProperty(name = "Storage System Groups")
    private Map<String, StorageSystemGroup> storageSystemGroups;

    @ConfigurableProperty(name = "Storage Archiver")
    private Archiver archiver;

    private volatile boolean dirty;

    @Override
    public void reconfigure(DeviceExtension from) {
        StorageDeviceExtension ext = (StorageDeviceExtension) from;
        ReconfiguringIterator.reconfigure(ext, this, StorageDeviceExtension.class);
    }

    public Map<String, StorageSystemGroup> getStorageSystemGroups() {
        return storageSystemGroups;
    }

    public void setStorageSystemGroups(Map<String, StorageSystemGroup> storageSystems) {
        this.storageSystemGroups = storageSystems;
        for (StorageSystemGroup storageSystem : storageSystems.values())
            storageSystem.setStorageDeviceExtension(this);
    }

    public StorageSystemGroup addStorageSystemGroup(StorageSystemGroup storageSystemGroup) {
        if (storageSystemGroups == null)
            storageSystemGroups = new TreeMap<String,StorageSystemGroup>();

        storageSystemGroup.setStorageDeviceExtension(this);
        StorageSystemGroup prev = storageSystemGroups.put(
                storageSystemGroup.getGroupID(), storageSystemGroup);
        if (prev != null)
            prev.setStorageDeviceExtension(null);
        return prev;
    }

    public StorageSystemGroup removeStorageSystemGroup(String groupID) {
        if (storageSystemGroups == null)
            return null;
        
        StorageSystemGroup prev = storageSystemGroups.remove(groupID);
        if (prev != null)
            prev.setStorageDeviceExtension(null);
        return prev;
    }

    public Collection<String> getStorageSystemGroupIDs() {
        if (storageSystemGroups == null)
            return Collections.emptySet();

        return storageSystemGroups.keySet();
    }

    public StorageSystemGroup getStorageSystemGroup(String groupID) {
        return storageSystemGroups.get(groupID);
    }

    public StorageSystem getStorageSystem(String groupID, String systemID) {
        StorageSystemGroup group = getStorageSystemGroup(groupID);
        return group != null ? group.getStorageSystem(systemID) : null;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public Archiver getArchiver() {
        return archiver;
    }

    public void setArchiver(Archiver archiver) {
        this.archiver = archiver;
    }

    public String getAffinityGroupID() {
        return System.getProperty(AFFINITY_GROUP_ID_PROPERTY);
    }

    public String getOlockHash() {
        return olockHash;
    }

    public void setOlockHash(String olockHash) {
        this.olockHash = olockHash;
    }
}
