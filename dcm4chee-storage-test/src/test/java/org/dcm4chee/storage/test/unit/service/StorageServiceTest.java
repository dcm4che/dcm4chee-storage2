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

package org.dcm4chee.storage.test.unit.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.Container;
import org.dcm4chee.storage.conf.FileCache;
import org.dcm4chee.storage.conf.StorageDevice;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.filecache.DefaultFileCacheProvider;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.service.StorageService;
import org.dcm4chee.storage.service.impl.StorageServiceImpl;
import org.dcm4chee.storage.test.unit.util.MockDicomConfiguration;
import org.dcm4chee.storage.test.unit.util.TransientDirectory;
import org.dcm4chee.storage.zip.ZipContainerProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class StorageServiceTest {

    private static final String NAME = "a/b/c";
    private static final Path SRC_PATH = Paths.get("target/test_file");
    private static final Path ZIP_PATH = Paths.get("src/test/data/test.zip");
    private static final Path CACHE_PATH = Paths.get("target/filecache/fs1/a/b/c");
    private static final String[] ENTRY_NAMES = { "entry-1", "entry-2", "entry-3" };
    private static final byte[] ENTRY = { 'e', 'n', 't', 'r', 'y' };
    private static final String DIGEST = "1043bfc77febe75fafec0c4309faccf1";

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(StorageServiceImpl.class)
            .addClass(FileSystemStorageSystemProvider.class)
            .addClass(DefaultFileCacheProvider.class)
            .addClass(ZipContainerProvider.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    StorageServiceImpl service;

    @Produces @StorageDevice
    static Device device = new Device("test");

//    @Produces
//    private static DicomConfiguration dicomConfiguration = new MockDicomConfiguration();

    @Rule
    public TransientDirectory storageDir = new TransientDirectory("target/test-storage");

    @Rule
    public TransientDirectory cacheDir = new TransientDirectory("target/filecache");

    @Rule
    public TransientDirectory journalDir = new TransientDirectory("target/journaldir");

    StorageDeviceExtension ext;
    StorageSystemGroup fsGroup;
    StorageSystem fs1;
    StorageSystem fs2;
    StorageSystem fs3;
    FileCache fileCache;
    Container container;
    ExecutorService executor;

    @Before
    public void setup() throws IOException {
        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        executor = Executors.newCachedThreadPool();
        device.setExecutor(executor);
        fsGroup = new StorageSystemGroup();
        fsGroup.setGroupID("fs");
        ext.addStorageSystemGroup(fsGroup);
        fsGroup.addStorageSystem(fs1 = createStorageSystem("fs1", "fs2"));
        fsGroup.addStorageSystem(fs2 = createStorageSystem("fs2", "fs3"));
        fsGroup.addStorageSystem(fs3 = createStorageSystem("fs3", "fs1"));
        fsGroup.setParallelism(2);
        container = new Container();
        container.setProviderName("org.dcm4chee.storage.zip");
        container.setChecksumEntry("MD5SUM");
        fsGroup.setContainer(container);
        fileCache = new FileCache();
        fileCache.setProviderName("org.dcm4chee.storage.filecache");
        fileCache.setFileCacheRootDirectory("target/filecache");
        fileCache.setJournalRootDirectory("target/journaldir");
        fsGroup.setFileCache(fileCache);
    }

    @After
    public void teardown() {
        device.removeDeviceExtension(ext);
        executor.shutdownNow();
        ext = null;
        fsGroup = null;
        fs1 = null;
        fs2 = null;
        fs3 = null;
    }
    
    @Produces
    DicomConfiguration createDicomConfiguration(Device device) {
        return new MockDicomConfiguration(device);
    }

    private StorageSystem createStorageSystem(String id, String next)
            throws IOException {
        StorageSystem system = new StorageSystem();
        system.setProviderName("org.dcm4chee.storage.filesystem");
        system.setStorageSystemID(id);
        system.setNextStorageSystemID(next);
        system.setStorageSystemPath("target/test-storage/" + id);
        system.setStorageSystemStatus(StorageSystemStatus.OK);
        system.setMountCheckFile("NO_MOUNT");
        Path dir = Files.createDirectories(
                Paths.get(system.getStorageSystemPath()));
        Files.deleteIfExists(dir.resolve(system.getMountCheckFile()));
        return system;
    }

    @Test
    public void testSelectStorageSystem() throws Exception {
        Assert.assertArrayEquals(
                new String[]{},
                fsGroup.getActiveStorageSystemIDs());
        Assert.assertSame(fs1, service.selectStorageSystem("fs", 0, false));
        Assert.assertArrayEquals(
                new String[]{"fs1", "fs2"},
                fsGroup.getActiveStorageSystemIDs());
        Assert.assertSame(fs2, service.selectStorageSystem("fs", 0, false));
        Assert.assertSame(fs1, service.selectStorageSystem("fs", 0, false));

        createMountCheckFile(fs2);
        Assert.assertSame(fs1, service.selectStorageSystem("fs", 0, false));
        Assert.assertArrayEquals(
                new String[]{"fs1", "fs3"},
                fsGroup.getActiveStorageSystemIDs());
        Assert.assertSame(fs3, service.selectStorageSystem("fs", 0, false));
        Assert.assertEquals("fs1", fsGroup.getNextStorageSystemID());

        deleteMountCheckFile(fs2);
        fs2.setStorageSystemStatus(StorageSystemStatus.OK);
        fs1.setReadOnly(true);
        Assert.assertSame(fs3, service.selectStorageSystem("fs", 0, false));
        Assert.assertSame(fs2, service.selectStorageSystem("fs", 0, false));
        Assert.assertArrayEquals(
                new String[]{"fs3", "fs2"},
                fsGroup.getActiveStorageSystemIDs());
        Assert.assertEquals("fs3", fsGroup.getNextStorageSystemID());

        fs2.setReadOnly(true);
        fs3.setReadOnly(true);
        Assert.assertNull(service.selectStorageSystem("fs", 0, false));
        Assert.assertArrayEquals(
                new String[]{},
                fsGroup.getActiveStorageSystemIDs());
    }

    @Test
    public void testOpenOutputStream() throws Exception {
        StorageContext ctx = service.createStorageContext(fs1);
        try ( OutputStream out = service.openOutputStream(ctx, NAME) ) {
            out.write(ENTRY);
        }
        Assert.assertEquals(ENTRY.length,
                Files.size(Paths.get(fs1.getStorageSystemPath(), NAME)));
    }

    @Test
    public void testOpenOutputStreamWithFileCache() throws Exception {
        fs1.setCacheOnStore(true);
        testOpenOutputStream();
        Assert.assertEquals(ENTRY.length, Files.size(CACHE_PATH));
    }

    @Test
    public void testCopyInputStream() throws Exception {
        StorageContext ctx = service.createStorageContext(fs1);
        try (ByteArrayInputStream in = new ByteArrayInputStream(ENTRY)) {
            service.copyInputStream(ctx, in, NAME);
        }
        Assert.assertEquals(ENTRY.length,
                Files.size(Paths.get(fs1.getStorageSystemPath(), NAME)));
    }

    @Test
    public void testCopyInputStreamWithFileCache() throws Exception {
        fs1.setCacheOnStore(true);
        testCopyInputStream();
        Assert.assertEquals(ENTRY.length, Files.size(CACHE_PATH));
    }

    @Test
    public void testStoreFile() throws Exception {
        StorageContext ctx = service.createStorageContext(fs1);
        makeSourceFile();
        service.storeFile(ctx, SRC_PATH, NAME);
        Assert.assertEquals(ENTRY.length,
                Files.size(Paths.get(fs1.getStorageSystemPath(), NAME)));
        Assert.assertTrue(Files.exists(SRC_PATH));
    }

    @Test
    public void testStoreFileWithFileCache() throws Exception {
        fs1.setCacheOnStore(true);
        testStoreFile();
        Assert.assertEquals(ENTRY.length, Files.size(CACHE_PATH));
    }

    @Test
    public void testMoveFile() throws Exception {
        StorageContext ctx = service.createStorageContext(fs1);
        makeSourceFile();
        service.moveFile(ctx, SRC_PATH, NAME);
        Assert.assertEquals(ENTRY.length,
                Files.size(Paths.get(fs1.getStorageSystemPath(), NAME)));
        Assert.assertFalse(Files.exists(SRC_PATH));
    }

    @Test
    public void testMoveFileWithFileCache() throws Exception {
        fs1.setCacheOnStore(true);
        testMoveFile();
        Assert.assertEquals(ENTRY.length, Files.size(CACHE_PATH));
    }

    @Test
    public void testStoreContainerEntries() throws Exception {
        StorageContext ctx = service.createStorageContext(fs1);
        service.storeContainerEntries(ctx, makeEntries(), NAME);
        Assert.assertEquals(Files.size(ZIP_PATH),
                Files.size(Paths.get(fs1.getStorageSystemPath(), NAME)));
    }

    @Test
    public void testStoreContainerEntriesWithFileCache() throws Exception {
        fs1.setCacheOnStore(true);
        testStoreContainerEntries();
        for (String name : ENTRY_NAMES) {
            Assert.assertEquals(ENTRY.length, Files.size(CACHE_PATH.resolve(name)));
        }
    }

    private void createMountCheckFile(StorageSystem system) throws IOException {
        Path mountCheckFile = Paths.get(
                system.getStorageSystemPath(),
                system.getMountCheckFile());
        Files.createFile(mountCheckFile);
    }

    private void deleteMountCheckFile(StorageSystem system) throws IOException {
        Path mountCheckFile = Paths.get(
                system.getStorageSystemPath(),
                system.getMountCheckFile());
        Files.delete(mountCheckFile);
    }

    private static void makeSourceFile() throws IOException {
        try (OutputStream out = Files.newOutputStream(SRC_PATH)) {
            out.write(ENTRY);
        }
    }

    private static List<ContainerEntry> makeEntries() throws IOException {
        makeSourceFile();
        ArrayList<ContainerEntry> entries =
                new ArrayList<ContainerEntry>(ENTRY_NAMES.length);
        for (String name : ENTRY_NAMES) {
            entries.add(new ContainerEntry.Builder(name, DIGEST).setSourcePath(SRC_PATH).build());
        }
        return entries;
    }
}
