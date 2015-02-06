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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.conf.Container;
import org.dcm4chee.storage.conf.FileCache;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.filecache.DefaultFileCacheProvider;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.service.RetrieveService;
import org.dcm4chee.storage.service.VerifyContainerException;
import org.dcm4chee.storage.service.impl.RetrieveServiceImpl;
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
public class RetrieveServiceTest {

    private static final int FILE_SIZE = 518;
    private static final String NAME = "test.zip";
    private static final String ENTRY_NAME = "entry-2";
    private static final String DIGEST = "1043bfc77febe75fafec0c4309faccf1";
    private static final int ENTRY_SIZE = 5;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(RetrieveServiceImpl.class)
            .addClass(FileSystemStorageSystemProvider.class)
            .addClass(DefaultFileCacheProvider.class)
            .addClass(ZipContainerProvider.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    RetrieveService service;

    @Produces
    static Device device = new Device("test");

    @Rule
    public TransientDirectory cacheDir = new TransientDirectory("target/filecache");

    @Rule
    public TransientDirectory journalDir = new TransientDirectory("target/journaldir");

    StorageDeviceExtension ext;
    StorageSystemGroup fsGroup;
    StorageSystem fs;
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
        fs = new StorageSystem();
        fs.setProviderName("org.dcm4chee.storage.filesystem");
        fs.setStorageSystemID("fs");
        fs.setStorageSystemPath("src/test/data");
        fsGroup.addStorageSystem(fs);
        container = new Container();
        container.setProviderName("org.dcm4chee.storage.zip");
        fileCache = new FileCache();
        fileCache.setProviderName("org.dcm4chee.storage.filecache");
        fileCache.setFileCacheRootDirectory("target/filecache");
        fileCache.setJournalRootDirectory("target/journaldir");
    }

    @After
    public void teardown() {
        executor.shutdownNow();
        device.removeDeviceExtension(ext);
        ext = null;
        fsGroup = null;
        fs = null;
    }

    @Test
    public void testOpenInputStream() throws Exception {
        RetrieveContext ctx = service.createRetrieveContext(fs);
        try ( InputStream in = service.openInputStream(ctx, NAME) ) {
            Assert.assertEquals(RetrieveServiceTest.FILE_SIZE,
                    readFully(in, RetrieveServiceTest.FILE_SIZE));
        }
    }

    private long readFully(InputStream in, int size) throws IOException {
        byte[] b = new byte[size];
        long l = 0;
        int n;
        while ((n = in.read(b)) > 0)
            l += n;
        return l;
    }

    @Test
    public void testGetFile() throws Exception {
        RetrieveContext ctx = service.createRetrieveContext(fs);
        Path file = service.getFile(ctx, NAME);
        Assert.assertEquals(RetrieveServiceTest.FILE_SIZE, Files.size(file));
    }

    @Test
    public void testOpenInputStreamWithFileCache() throws Exception {
        fsGroup.setFileCache(fileCache);
        RetrieveContext ctx = service.createRetrieveContext(fs);
        try ( InputStream in = service.openInputStream(ctx, NAME) ) {
            Assert.assertEquals(RetrieveServiceTest.FILE_SIZE,
                    readFully(in, RetrieveServiceTest.FILE_SIZE));
        }
    }

    @Test
    public void testGetFileWithFileCache() throws Exception {
        fsGroup.setFileCache(fileCache);
        RetrieveContext ctx = service.createRetrieveContext(fs);
        Path file = service.getFile(ctx, NAME);
        Assert.assertEquals(RetrieveServiceTest.FILE_SIZE, Files.size(file));
    }

    @Test
    public void testGetEntryInputStream() throws Exception {
        fsGroup.setContainer(container);
        RetrieveContext ctx = service.createRetrieveContext(fs);
        try ( InputStream in = service.openInputStream(ctx, NAME, ENTRY_NAME) ) {
            Assert.assertEquals(RetrieveServiceTest.ENTRY_SIZE,
                    readFully(in, RetrieveServiceTest.ENTRY_SIZE));
        }
    }

    @Test
    public void testGetEntryFile() throws Exception {
        fsGroup.setContainer(container);
        fsGroup.setFileCache(fileCache);
        RetrieveContext ctx = service.createRetrieveContext(fs);
        Path file = service.getFile(ctx, NAME, ENTRY_NAME);
        Assert.assertEquals(RetrieveServiceTest.ENTRY_SIZE, Files.size(file));
    }

    @Test
    public void testVerifyContainer() throws Exception {
        fsGroup.setContainer(container);
        fsGroup.setFileCache(fileCache);
        String[] entryNames = { "entry-1", "entry-2", "entry-3" };
        List<ContainerEntry> entries = new ArrayList<ContainerEntry>(
                entryNames.length);
         for (String name : entryNames)
             entries.add(new ContainerEntry.Builder(name, DIGEST).build());
         RetrieveContext ctx = service.createRetrieveContext(fs);
         service.verifyContainer(ctx, NAME, entries);
    }

    @Test(expected = VerifyContainerException.class)
    public void testVerifyContainerThrowsException() throws Exception {
        fsGroup.setContainer(container);
        fsGroup.setFileCache(fileCache);
        ContainerEntry entry = new ContainerEntry.Builder("missing", DIGEST)
                .build();
        RetrieveContext ctx = service.createRetrieveContext(fs);
        service.verifyContainer(ctx, NAME, Arrays.asList(entry));
    }

    @Test
    public void testResolveContainerEntries() throws Exception {
        fsGroup.setContainer(container);
        fsGroup.setFileCache(fileCache);
        ContainerEntry entry = new ContainerEntry.Builder(NAME, DIGEST)
                .setSourceStorageSystemGroupID(fsGroup.getGroupID())
                .setSourceStorageSystemID(fs.getStorageSystemID())
                .setSourceName(NAME).build();
        service.resolveContainerEntries(Arrays.asList(entry));
        Path file = entry.getSourcePath();
        Assert.assertNotNull(file);
        Assert.assertTrue(Files.exists(file));
        Assert.assertEquals(RetrieveServiceTest.FILE_SIZE, Files.size(file));
    }

    @Test(expected = IllegalStateException.class)
    public void testResolveContainerEntriesThrowsException()
            throws Exception {
        fsGroup.setContainer(container);
        fsGroup.setFileCache(fileCache);
        ContainerEntry entry = new ContainerEntry.Builder(NAME, DIGEST).build();
        service.resolveContainerEntries(Arrays.asList(entry));
    }
}
