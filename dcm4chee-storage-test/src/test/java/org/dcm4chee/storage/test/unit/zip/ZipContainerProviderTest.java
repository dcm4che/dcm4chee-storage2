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
 * Portions created by the Initial Developer are Copyright (C) 2011-2014
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

package org.dcm4chee.storage.test.unit.zip;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.Container;
import org.dcm4chee.storage.conf.FileCache;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.spi.ContainerProvider;
import org.dcm4chee.storage.spi.FileCacheProvider;
import org.dcm4chee.storage.zip.ZipContainerProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.name.Named;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class ZipContainerProviderTest {

    private static final String FILE_RESOURCE = "entry.bin";
    private static final String ZIP_RESOURCE = "test.zip";
    private static final String DIGEST = "d292107e992e8e1c97882e3d7a14c96d";
    private static final String ZIP_PATH = "target/test-storage/zip/test.zip";
    private static final String TEMP_DIR = "target/test-storage/zip/tmp";
    private static final String ENTRY_NAME = "entry-2";
    private static final String[] ENTRY_NAMES = { "entry-1", ENTRY_NAME, "entry-3" };

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(FileSystemStorageSystemProvider.class)
            .addClass(ZipContainerProvider.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Produces
    static Device device = new Device("test");

    @Inject @Named("org.dcm4chee.storage.zip")
    ContainerProvider provider;

    ExecutorService executor; 
    Path srcFilePath;
    Path srcZipPath;
    Path targetZipPath;
    Path tempDirPath;
    Container container;
    StorageContext storageCtx; 
    RetrieveContext retrieveCtx;

    @Before
    public void setup() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        device.setExecutor(executor);
        container = new Container();
        container.setChecksumEntry("MD5SUM");
        provider.init(container);
        storageCtx = new StorageContext();
        storageCtx.setArchiverProvider(provider);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setContainerProvider(provider);
        retrieveCtx.setFileCacheProvider(newFileCacheProvider());
        srcFilePath = getResourcePath(FILE_RESOURCE);
        srcZipPath = getResourcePath(ZIP_RESOURCE);
        targetZipPath = Paths.get(ZIP_PATH);
        tempDirPath =  Paths.get(TEMP_DIR);
        Files.deleteIfExists(targetZipPath);
        deleteDir(tempDirPath);
    }

    @After
    public void teardown() {
        executor.shutdown();
    }

    @Test
    public void testWriteEntriesTo() throws Exception {
        Files.createDirectories(targetZipPath.getParent());
        try ( OutputStream out = Files.newOutputStream(targetZipPath) ) {
            provider.writeEntriesTo(storageCtx, makeEntries(srcFilePath), out);
        }
        assertEquals(Files.size(srcZipPath), Files.size(targetZipPath));
    }

    @Test
    public void testSeekEntry() throws Exception {
        Path targetFilePath = tempDirPath.resolve(ENTRY_NAME);
        Files.createDirectories(targetFilePath.getParent());
        try (InputStream in = Files.newInputStream(srcZipPath)) {
            Files.copy(provider.seekEntry(
                    retrieveCtx, ZIP_RESOURCE, ENTRY_NAME, in),
                    targetFilePath);
        }
        assertEquals(Files.size(srcFilePath), Files.size(targetFilePath));
    }

    @Test
    public void testGetFile() throws Exception {
        Path targetFilePath = tempDirPath.resolve(ENTRY_NAME);
        Files.createDirectories(targetFilePath.getParent());
        InputStream in = Files.newInputStream(srcZipPath);
        targetFilePath = provider.getFile(retrieveCtx, ZIP_RESOURCE, ENTRY_NAME, in);
        assertEquals(Files.size(srcFilePath), Files.size(targetFilePath));
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir))
            return;

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
        
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                FileVisitResult result = super.visitFile(file, attrs);
                if (result == FileVisitResult.CONTINUE)
                    Files.delete(file);
                return result;
            }
        
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                FileVisitResult result = super.postVisitDirectory(dir, exc);
                if (result == FileVisitResult.CONTINUE)
                    Files.delete(dir);
                return result;
            }
        });
    }

    private FileCacheProvider newFileCacheProvider() {
        return new FileCacheProvider() {

            @Override
            public void init(FileCache retrieveCache) {
            }

            @Override
            public Path toPath(RetrieveContext ctx, String name, String entryName) {
                return tempDirPath.resolve(entryName);
            }

            @Override
            public boolean exists(Path path) {
                return false;
            }

            @Override
            public void register(Path path) {
            }};
    }

    private static Path getResourcePath(String name) {
        try {
            return Paths.get(Thread.currentThread().getContextClassLoader()
                    .getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<ContainerEntry> makeEntries(Path src) {
        ArrayList<ContainerEntry> entries =
                new ArrayList<ContainerEntry>(ENTRY_NAMES.length);
        for (String name : ENTRY_NAMES) {
            entries.add(new ContainerEntry(name, src, DIGEST));
        }
        return entries;
    }

}
