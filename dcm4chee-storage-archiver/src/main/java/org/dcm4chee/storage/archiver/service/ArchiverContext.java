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

package org.dcm4chee.storage.archiver.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.dcm4chee.storage.ContainerEntry;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
public class ArchiverContext implements Serializable {

    private static final long serialVersionUID = 984506197890636234L;

    private ArrayList<ContainerEntry> entries;
    private final String storageSystemGroupID;
    private final String name;
    private String storageSystemID;
    private boolean notInContainer;
    private String objectStatus;
    private HashMap<String, Serializable> properties = new HashMap<String, Serializable>();

    public ArchiverContext(String name, String storageSystemGroupID) {
        this.name = name;
        this.storageSystemGroupID = storageSystemGroupID;
    }

    public String getName() {
        return name;
    }

    public String getStorageSystemGroupID() {
        return storageSystemGroupID;
    }

    public String getStorageSystemID() {
        return storageSystemID;
    }

    public void setStorageSystemID(String storageSystemID) {
        this.storageSystemID = storageSystemID;
    }

    public List<ContainerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void setEntries(List<ContainerEntry> entries) {
        this.entries = new ArrayList<ContainerEntry>(entries);
    }

    public Serializable getProperty(String key) {
        return properties.get(key);
    }

    public Serializable removeProperty(String key) {
        return properties.remove(key);
    }

    public void setProperty(String key, Serializable value) {
        properties.put(key, value);
    }

    public boolean isNotInContainer() {
        return notInContainer;
    }

    public void setNotInContainer(boolean notInContainer) {
        this.notInContainer = notInContainer;
    }

    public String getObjectStatus() {
        return objectStatus;
    }

    public void setObjectStatus(String objectStatus) {
        this.objectStatus = objectStatus;
    }
}
