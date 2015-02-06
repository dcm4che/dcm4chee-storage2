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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.service.StorageService;
import org.dcm4chee.storage.spi.ContainerProvider;
import org.dcm4chee.storage.spi.FileCacheProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@ApplicationScoped
public class StorageServiceImpl implements StorageService {

    private static final Logger LOG =
            LoggerFactory.getLogger(StorageServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private Instance<StorageSystemProvider> storageSystemProviders;

    @Inject
    private Instance<ContainerProvider> containerProviders;

    @Inject
    private Instance<FileCacheProvider> fileCacheProviders;

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

    @Override
    public Path getBaseDirectory(StorageSystem system) {
        StorageSystemProvider provider =
                system.getStorageSystemProvider(storageSystemProviders);
        return provider.getBaseDirectory(system);
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
                LOG.info("Update Status of {} to FULL", system);
                system.setStorageSystemStatus(StorageSystemStatus.FULL);
                system.getStorageDeviceExtension().setDirty(true);
                return false;
            }
        } catch (IOException e) {
            LOG.warn("Update Status of {} to NOT_ACCESSABLE caused by", system, e);
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
        ctx.setContainerProvider(
                storageSystem.getContainerProvider(containerProviders));
        if (storageSystem.isCacheOnStore())
            ctx.setFileCacheProvider(
                    storageSystem.getFileCacheProvider(fileCacheProviders));
        ctx.setStorageSystem(storageSystem);
        return ctx;
    }

    @Override
    public OutputStream openOutputStream(StorageContext ctx, String name)
            throws IOException {
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        FileCacheProvider fileCacheProvider = ctx.getFileCacheProvider();
        provider.checkWriteable();
        LOG.info("Storing stream to {}@{}", name, ctx.getStorageSystem());
        if (fileCacheProvider == null)
            return provider.openOutputStream(ctx, name);

        Path cachedFile = fileCacheProvider.toPath(ctx, name);
        fileCacheProvider.register(cachedFile);
        Files.createDirectories(cachedFile.getParent());
        try {
            return new FileCacheOutputStream(ctx, name, cachedFile);
        } catch (FileAlreadyExistsException e) {
            throw new ObjectAlreadyExistsException(
                    ctx.getStorageSystem().getStorageSystemPath(), name, e);
        }
    }

    private static class FileCacheOutputStream extends FilterOutputStream {

        private StorageContext ctx;
        private String name;
        private Path path;

        public FileCacheOutputStream(StorageContext ctx, String name, Path path)
                throws IOException {
            super(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW));
            this.ctx = ctx;
            this.name = name;
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            super.close();
            ctx.getStorageSystemProvider().storeFile(ctx, path, name);
        }
    }

    @Override
    public void copyInputStream(StorageContext ctx, InputStream in,
            String name) throws IOException {
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        FileCacheProvider fileCacheProvider = ctx.getFileCacheProvider();
        provider.checkWriteable();
        if (fileCacheProvider != null) {
            Path cachedFile = fileCacheProvider.toPath(ctx, name);
            fileCacheProvider.register(cachedFile);
            Files.createDirectories(cachedFile.getParent());
            try {
                Files.copy(in, cachedFile);
            } catch (FileAlreadyExistsException e) {
                throw new ObjectAlreadyExistsException(
                        ctx.getStorageSystem().getStorageSystemPath(), name, e);
            }
            provider.storeFile(ctx, cachedFile, name);
        } else
            provider.copyInputStream(ctx, in, name);
        LOG.info("Copied stream to {}@{}", name, ctx.getStorageSystem());
    }

    @Override
    public void storeContainerEntries(StorageContext ctx,
            List<ContainerEntry> entries, String name) throws IOException {
        ContainerProvider containerProvider = ctx.getContainerProvider();
        if (containerProvider == null)
            throw new UnsupportedOperationException();
        
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        provider.checkWriteable();
        try ( OutputStream out = provider.openOutputStream(ctx, name)) {
            containerProvider.writeEntriesTo(ctx, entries, out);
        }
        LOG.info("Stored Entries to {}@{}", name, ctx.getStorageSystem());
        FileCacheProvider fileCacheProvider = ctx.getFileCacheProvider();
        if (fileCacheProvider != null) {
            for (ContainerEntry entry : entries) {
                Path cachedFile = fileCacheProvider
                        .toPath(ctx, name).resolve(entry.getName());
                Files.createDirectories(cachedFile.getParent());
                Files.copy(entry.getSourcePath(), cachedFile);
                fileCacheProvider.register(cachedFile);
            }
        }
    }

    @Override
    public void storeFile(StorageContext ctx, Path path, String name)
            throws IOException {
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        FileCacheProvider fileCacheProvider = ctx.getFileCacheProvider();
        provider.checkWriteable();
        provider.storeFile(ctx, path, name);
        if (fileCacheProvider != null) {
            Path cachedFile = fileCacheProvider.toPath(ctx, name);
            Files.createDirectories(cachedFile.getParent());
            Files.copy(path, cachedFile);
            fileCacheProvider.register(cachedFile);
        }
        LOG.info("Stored File {} to {}@{}", path, name, ctx.getStorageSystem());
    }

    @Override
    public void moveFile(StorageContext ctx, Path path, String name)
            throws IOException {
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        FileCacheProvider fileCacheProvider = ctx.getFileCacheProvider();
        provider.checkWriteable();
        if (fileCacheProvider != null) {
            provider.storeFile(ctx, path, name);
            Path cachedFile = fileCacheProvider.toPath(ctx, name);
            Files.createDirectories(cachedFile.getParent());
            Files.move(path, cachedFile);
            fileCacheProvider.register(cachedFile);
        } else {
            provider.moveFile(ctx, path, name);
        }
        LOG.info("Moved File {} to {}@{}", path, name, ctx.getStorageSystem());
    }

    @Override
    public void deleteObject(StorageContext context, String name)
            throws IOException {
        StorageSystemProvider provider = context.getStorageSystemProvider();
        provider.checkWriteable();
        provider.deleteObject(context, name);
        LOG.info("Delete Object {}@{}", name, context.getStorageSystem());
   }

}
