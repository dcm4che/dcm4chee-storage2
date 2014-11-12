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

package org.dcm4chee.storage.cloud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.ByteSourcePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jclouds.Constants.*;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@Named("org.dcm4chee.storage.cloud")
@Dependent
public class CloudStorageSystemProvider implements StorageSystemProvider {

    private static Logger log = LoggerFactory
            .getLogger(CloudStorageSystemProvider.class);

    private StorageSystem system;
    private BlobStoreContext context;

    @Override
    public void init(StorageSystem storageSystem) {
        this.system = storageSystem;
        Properties overrides = new Properties();
        overrides.setProperty(PROPERTY_CONNECTION_TIMEOUT,
                String.valueOf(storageSystem.getConnectTimeout()));
        overrides.setProperty(PROPERTY_SO_TIMEOUT,
                String.valueOf(storageSystem.getSocketTimeout()));
        context = ContextBuilder
                .newBuilder(storageSystem.getStorageSystemAPI())
                .credentials(storageSystem.getStorageSystemIdentity(),
                        storageSystem.getStorageSystemCredential())
                .overrides(overrides)
                .endpoint(storageSystem.getStorageSystemURI())
                .buildView(BlobStoreContext.class);
    }

    @Override
    public StorageSystemStatus checkStatus(long minFreeSize) {
        return StorageSystemStatus.OK;
    }

    @Override
    public OutputStream openOutputStream(StorageContext ctx, final String name)
            throws IOException {
        if (!system.getSupportsChunkedEncoding()) {
            // TODO: content length needs to be calculated ahead of time for
            // providers that do not support chunked encoding (e.g. aws-s3)
            throw new UnsupportedOperationException();
        }

        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(out);
        final BlobStore blobStore = context.getBlobStore();
        final Blob blob = blobStore.blobBuilder(name).payload(in).build();
        Device device = system.getStorageSystemGroup()
                .getStorageDeviceExtension().getDevice();
        device.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    blobStore.putBlob(system.getStorageSystemContainer(), blob);
                } catch (RuntimeException e) {
                    log.error(
                            "Unable to store " + name + " to :"
                                    + system.getStorageSystemURI() + '/'
                                    + system.getStorageSystemContainer(), e);
                }
            }
        });
        return out;
    }

    @Override
    public void storeFile(StorageContext ctx, Path source, String name)
            throws IOException {
        BlobStore blobStore = context.getBlobStore();
        Payload payload = new ByteSourcePayload(
                com.google.common.io.Files.asByteSource(source.toFile()));
        Blob blob = blobStore.blobBuilder(name).payload(payload)
                .contentLength(Files.size(source)).build();
        blobStore.putBlob(system.getStorageSystemContainer(), blob);
    }

    @Override
    public void moveFile(StorageContext ctx, Path source, String name)
            throws IOException {
        storeFile(ctx, source, name);
        Files.delete(source);
    }

    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name)
            throws IOException {
        BlobStore blobStore = context.getBlobStore();
        String container = system.getStorageSystemContainer();
        Blob blob = blobStore.getBlob(container, name);
        if (blob == null) {
            throw new ObjectNotFoundException(system.getStorageSystemURI(),
                    container + '/' + name);
        }
        return blob.getPayload().openStream();
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name) throws IOException {
        // InputStream in = openInputStream(ctx, name);
        // FileCacheProvider fcp = ctx.getFileCacheProvider();
        throw new UnsupportedOperationException();
        // TODO
    }

    @Override
    public void close() throws IOException {
        if (context != null)
            context.close();
    }
}
