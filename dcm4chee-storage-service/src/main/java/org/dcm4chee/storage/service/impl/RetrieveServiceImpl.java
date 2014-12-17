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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.storage.ExtractTask;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.service.RetrieveService;
import org.dcm4chee.storage.spi.ContainerProvider;
import org.dcm4chee.storage.spi.FileCacheProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@ApplicationScoped
public class RetrieveServiceImpl implements RetrieveService {

    private static final Logger LOG =
            LoggerFactory.getLogger(StorageServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private Instance<StorageSystemProvider> storageSystemProviders;

    @Inject
    private Instance<ContainerProvider> archiverProviders;

    @Inject
    private Instance<FileCacheProvider> fileCacheProviders;

    private final ConcurrentHashMap<ExtractTaskKey, ExtractTask> extractTasks =
            new ConcurrentHashMap<ExtractTaskKey, ExtractTask>();

    public StorageSystem getStorageSystem(String groupID, String systemID) {
        StorageDeviceExtension devExt =
                device.getDeviceExtension(StorageDeviceExtension.class);
        return devExt.getStorageSystem(groupID, systemID);
    }

    @Override
    public RetrieveContext createRetrieveContext(StorageSystem storageSystem) {
        RetrieveContext ctx = new RetrieveContext();
        ctx.setStorageSystemProvider(
                storageSystem.getStorageSystemProvider(storageSystemProviders));
        ctx.setContainerProvider(
                storageSystem.getArchiverProvider(archiverProviders));
        ctx.setFileCacheProvider(
                storageSystem.getFileCacheProvider(fileCacheProviders));
        ctx.setStorageSystem(storageSystem);
        return ctx;
    }

    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name)
            throws IOException {
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        return provider.openInputStream(ctx, name);
    }

    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name,
            String entryName) throws IOException, InterruptedException {
        if (ctx.getFileCacheProvider() != null)
            return Files.newInputStream(getFile(ctx, name, entryName));

        ContainerProvider archiverProvider = ctx.getContainerProvider();
        if (archiverProvider == null)
            throw new UnsupportedOperationException();

        InputStream in = openInputStream(ctx, name);
        try {
            return archiverProvider.seekEntry(ctx, name, entryName, in);
        } catch (IOException e) {
            SafeClose.close(in);
            throw e;
        }
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name) throws IOException {
        StorageSystemProvider provider = ctx.getStorageSystemProvider();
        return provider.getFile(ctx, name);
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name, String entryName)
            throws IOException, InterruptedException {
        ContainerProvider archiverProvider = ctx.getContainerProvider();
        if (archiverProvider == null)
            throw new UnsupportedOperationException();

        FileCacheProvider fileCacheProvider = ctx.getFileCacheProvider();
        if (fileCacheProvider == null)
            throw new UnsupportedOperationException();

        Path path = fileCacheProvider.toPath(ctx, name, entryName);
        if (!fileCacheProvider.exists(path))
            return path;

        if (getExtractTask(ctx, name).getFile(entryName) == null
                && !fileCacheProvider.exists(path))
            throw new ObjectNotFoundException(
                    ctx.getStorageSystem().getStorageSystemPath(), name, entryName);

        return path;
    }

    private ExtractTask getExtractTask(final RetrieveContext ctx, final String name) {
        final ExtractTaskKey key = new ExtractTaskKey(ctx.getStorageSystem(), name);
        final ExtractTask newTask = new ExtractTaskImpl();
        ExtractTask prevTask = extractTasks.putIfAbsent(key, newTask);
        if (prevTask != null)
            return prevTask;

        device.execute(new Runnable(){

            @Override
            public void run() {
                try {
                    ctx.getContainerProvider().extractEntries(ctx, name, newTask);
                } catch (IOException ex) {
                    newTask.exception(ex);
                }
                newTask.finished();
                extractTasks.remove(key);
            }});

        return newTask;
    }

}
