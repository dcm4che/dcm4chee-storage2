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

package org.dcm4chee.storage.archiver.conf;

import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;
import org.dcm4che3.conf.core.api.LDAP;
import org.dcm4che3.net.DeviceExtension;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@LDAP(objectClasses = "dcmArchiverDeviceExtension")
@ConfigurableClass
public class ArchiverDeviceExtension extends DeviceExtension {

    @ConfigurableProperty(name = "dcmArchiverStoreMaxRetries", defaultValue = "24")
    private int archiverStoreMaxRetries = 24;

    @ConfigurableProperty(name = "dcmArchiverRetryInterval", defaultValue = "3600")
    private int archiverStoreRetryInterval = 3600;

    @ConfigurableProperty(name = "dcmArchiverVerifyAfterStore", defaultValue = "true")
    private boolean verifyAfterStore = true;

    public int getArchiverStoreMaxRetries() {
        return archiverStoreMaxRetries;
    }

    public void setArchiverStoreMaxRetries(int storeMaxRetries) {
        this.archiverStoreMaxRetries = storeMaxRetries;
    }

    public int getArchiverStoreRetryInterval() {
        return archiverStoreRetryInterval;
    }

    public void setArchiverStoreRetryInterval(int storeRetryInterval) {
        this.archiverStoreRetryInterval = storeRetryInterval;
    }

    public boolean getVerifyAfterStore() {
        return verifyAfterStore;
    }

    public void setVerifyAfterStore(boolean verifyAfterStore) {
        this.verifyAfterStore = verifyAfterStore;
    }
}
