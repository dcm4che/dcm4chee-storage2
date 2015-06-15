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

package org.dcm4chee.storage.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
public interface StorageService {

    StorageSystem selectStorageSystem(String storageSystemGroupID, long size);

    Path getBaseDirectory(StorageSystem storageSystem);

    StorageSystem getStorageSystem(String groupID, String systemID);

    StorageContext createStorageContext(StorageSystem storageSystem);

    OutputStream openOutputStream(StorageContext context, String name)
            throws IOException;

    void copyInputStream(StorageContext context, InputStream in, String name)
            throws IOException;

    void storeContainerEntries(StorageContext context,
            List<ContainerEntry> entries, String name) throws IOException;

    void storeFile(StorageContext context, Path path, String name) throws IOException;

    void moveFile(StorageContext context, Path path, String name) throws IOException;

    void deleteObject(StorageContext context, String name) throws IOException;
}
