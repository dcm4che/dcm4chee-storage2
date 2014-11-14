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

package org.dcm4chee.storage.service.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.service.ArchiveOutputStream;
import org.dcm4chee.storage.service.StorageService;
import org.dcm4chee.storage.spi.ArchiverProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
public class StorageServiceImpl implements StorageService {

    @Inject
    private Device device;

    @Inject
    private Instance<StorageSystemProvider> storageSystemProviders;

    @Inject
    private Instance<ArchiverProvider> archiverProviders;

    @Override
    public StorageSystem selectStorageSystem(String groupID, long reserveSpace) {
        StorageDeviceExtension ext =
                device.getDeviceExtension(StorageDeviceExtension.class);
        StorageSystemGroup group = ext.getStorageSystemGroup(groupID);
        if (group == null)
            throw new IllegalArgumentException("No such Storage System Group - "
                    + groupID);
        
        StorageSystem system = group.nextActiveStorageSystem();
        while (system != null && !checkMinFreeSpace(system, reserveSpace)) {
            group.deactivate(system);
            group.getStorageDeviceExtension().setDirty(true);
            system = group.getNextStorageSystem();
            while (system != null && !checkMinFreeSpace(system, reserveSpace))
                system = system.getNextStorageSystem();
            if (system != null)
                group.activate(system, true);
            system = group.nextActiveStorageSystem();
        }
        return system;
    }

    public boolean checkMinFreeSpace(StorageSystem system, long reserveSpace) {
        if (!system.installed())
            return false;
        if (system.isReadOnly())
            return false;
        if (system.getStorageSystemStatus() != StorageSystemStatus.OK)
            return false;

        StorageSystemProvider provider =
                system.getStorageSystemProvider(storageSystemProviders);

        try {
            provider.checkWriteable();
            if (system.getMinFreeSpace() != null
                    &&provider.getUsableSpace() 
                            < system.getMinFreeSpaceInBytes() + reserveSpace) {
                        system.setStorageSystemStatus(StorageSystemStatus.FULL);
                        system.getStorageDeviceExtension().setDirty(true);
                        return false;
            }
        } catch (IOException e) {
            system.setStorageSystemStatus(StorageSystemStatus.NOT_ACCESSABLE);
            system.getStorageDeviceExtension().setDirty(true);
            return false;
        }
        return true;
    }

    @Override
    public StorageContext createStorageContext(StorageSystem storageSystem) {
        StorageContext ctx = new StorageContext();
        ctx.setStorageSystemProvider(
                storageSystem.getStorageSystemProvider(storageSystemProviders));
        ctx.setArchiverProvider(
                storageSystem.getArchiverProvider(archiverProviders));
        return ctx;
    }

    @Override
    public OutputStream openOutputStream(StorageContext context, String name) throws IOException {
        if (context.getArchiverProvider() != null)
            return openArchiveOutputStream(context, name);

        StorageSystemProvider provider = context.getStorageSystemProvider();
        provider.checkWriteable();
        return provider.openOutputStream(context, name);
    }

    @Override
    public ArchiveOutputStream openArchiveOutputStream(StorageContext context,
            String name) {
        // if (context.getArchiverProvider() == null)
            throw new UnsupportedOperationException();
        //TODO
    }

    @Override
    public void storeFile(StorageContext context, Path path, String name)
            throws IOException {
        if (context.getArchiverProvider() != null)
            throw new UnsupportedOperationException();

        StorageSystemProvider provider = context.getStorageSystemProvider();
        provider.checkWriteable();
        provider.storeFile(context, path, name);
    }

    @Override
    public void moveFile(StorageContext context, Path path, String name)
            throws IOException {
        if (context.getArchiverProvider() != null)
            throw new UnsupportedOperationException();

        StorageSystemProvider provider = context.getStorageSystemProvider();
        provider.checkWriteable();
        provider.moveFile(context, path, name);
    }

    @Override
    public void deleteObject(StorageContext context, String name)
            throws IOException {
        StorageSystemProvider provider = context.getStorageSystemProvider();
        provider.checkWriteable();
        provider.deleteObject(context, name);
    }

}
