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

package org.dcm4chee.storage.test.unit.cloud;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import junit.framework.Assert;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.cloud.CloudStorageSystemProvider;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@RunWith(Arquillian.class)
public class CloudStorageSystemProviderTest {

    private static final String ID1 = "a/b/c";
    private static final String ID2 = "x/y/z";
    private static final String CONTAINER = "test-container";
    private static final String FS_PATH = "target/test-storage/cloud";
    private static final Path DIR = Paths.get(FS_PATH + '/' + CONTAINER);
    private static final Path FILE1 = DIR.resolve(ID1);
    private static final Path FILE2 = DIR.resolve(ID2);
    private static final String API = "filesystem";

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClass(CloudStorageSystemProvider.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    @Named("org.dcm4chee.storage.cloud")
    StorageSystemProvider provider;

    @Produces
    static Device device = new Device("test");

    StorageDeviceExtension ext;
    StorageSystemGroup group;
    StorageSystem system;
    StorageContext storageCtx;
    RetrieveContext retrieveCtx;
    ExecutorService executor;

    @Before
    public void setup() throws IOException {
        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        executor = Executors.newCachedThreadPool();
        device.setExecutor(executor);
        group = new StorageSystemGroup();
        group.setGroupID("cloud");
        ext.addStorageSystemGroup(group);
        system = new StorageSystem();
        system.setStorageSystemGroup(group);
        system.setStorageSystemID("cloud");
        system.setStorageSystemStatus(StorageSystemStatus.OK);
        system.setStorageSystemAPI(API);
        system.setStorageSystemPath(FS_PATH);
        system.setStorageSystemContainer(CONTAINER);
        provider.init(system);
        storageCtx = new StorageContext();
        storageCtx.setStorageSystemProvider(provider);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setStorageSystemProvider(provider);

        if (Files.exists(FILE2))
            Files.delete(FILE2);
        if (!Files.exists(FILE1)) {
            Files.createDirectories(FILE1.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(FILE1,
                    StandardOpenOption.CREATE)) {
                writer.write("testdata");
            }
        }
    }

    @After
    public void teardown() {
        executor.shutdownNow();
        device.removeDeviceExtension(ext);
        ext = null;
        group = null;
        system = null;
    }

    @Test
    public void testOpenOutputStream() throws IOException {
        Assert.assertFalse(Files.exists(FILE2));
        try (OutputStream out = provider.openOutputStream(storageCtx, ID2)) {
            Files.copy(FILE1, out);
        }
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertEquals(Files.size(FILE1), storageCtx.getFileSize());
    }

    @Test(expected = ObjectAlreadyExistsException.class)
    public void testOpenOutputStreamWithException() throws IOException {
        provider.openOutputStream(storageCtx, ID1).close();
    }

    @Test
    public void testStoreFile() throws IOException {
        Assert.assertFalse(Files.exists(FILE2));
        provider.storeFile(storageCtx, FILE1, ID2);
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertEquals(Files.size(FILE2), storageCtx.getFileSize());
    }

    @Test(expected = ObjectAlreadyExistsException.class)
    public void testStoreFileWithException() throws IOException {
        provider.storeFile(storageCtx, FILE1, ID1);
    }

    @Test
    public void testCopyInputStream() throws IOException {
        try (InputStream in = Files.newInputStream(FILE1,
                StandardOpenOption.READ)) {
            provider.copyInputStream(storageCtx, in, ID2);
        }
        Assert.assertEquals(Files.size(FILE2), storageCtx.getFileSize());
    }

    @Test
    public void testMoveFile() throws IOException {
        Assert.assertTrue(Files.exists(FILE1));
        Assert.assertFalse(Files.exists(FILE2));
        provider.moveFile(storageCtx, FILE1, ID2);
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertFalse(Files.exists(FILE1));
    }

    @Test
    public void testDeleteObject() throws IOException {
        Assert.assertTrue(Files.exists(FILE1));
        provider.deleteObject(storageCtx, ID1);
        Assert.assertFalse(Files.exists(FILE1));
    }

    @Test(expected = ObjectNotFoundException.class)
    public void testDeleteObjectWithException() throws IOException {
        provider.deleteObject(storageCtx, ID2);
    }

    @Test
    public void testOpenInputStream() throws IOException {
        provider.openInputStream(retrieveCtx, ID1).close();
    }

    @Test(expected = ObjectNotFoundException.class)
    public void testOpenInputStreamWithException() throws IOException {
        provider.openInputStream(retrieveCtx, ID2).close();
    }

    @Test
    public void testGetFile() throws IOException {
        // TODO
    }
}
