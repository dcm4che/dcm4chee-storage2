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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.ExtractTask;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.Container;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.spi.ContainerProvider;
import org.dcm4chee.storage.test.unit.util.TransientDirectory;
import org.dcm4chee.storage.zip.ZipContainerProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class ZipContainerProviderTest {

    private static final String DIGEST = "1043bfc77febe75fafec0c4309faccf1";
    private static final String DIR_PATH = "target/test-storage/zip";
    private static final String NAME = "test.zip";
    private static final String ENTRY_FILE = "entry";
    private static final String[] ENTRY_NAMES = { "entry-1", "entry-2", "entry-3" };
    private static final String ENTRY_NAME = ENTRY_NAMES[1];
    private static final byte[] ENTRY = { 'e', 'n', 't', 'r', 'y' };
    private static final byte[] ZIP = { 80, 75, 3, 4, 10, 0, 0, 8, 0, 0, 59,
            75, -116, 69, -34, -12, 123, 2, 123, 0, 0, 0, 123, 0, 0, 0, 6, 0,
            0, 0, 77, 68, 53, 83, 85, 77, 49, 48, 52, 51, 98, 102, 99, 55, 55,
            102, 101, 98, 101, 55, 53, 102, 97, 102, 101, 99, 48, 99, 52, 51,
            48, 57, 102, 97, 99, 99, 102, 49, 32, 101, 110, 116, 114, 121, 45,
            49, 10, 49, 48, 52, 51, 98, 102, 99, 55, 55, 102, 101, 98, 101, 55,
            53, 102, 97, 102, 101, 99, 48, 99, 52, 51, 48, 57, 102, 97, 99, 99,
            102, 49, 32, 101, 110, 116, 114, 121, 45, 50, 10, 49, 48, 52, 51,
            98, 102, 99, 55, 55, 102, 101, 98, 101, 55, 53, 102, 97, 102, 101,
            99, 48, 99, 52, 51, 48, 57, 102, 97, 99, 99, 102, 49, 32, 101, 110,
            116, 114, 121, 45, 51, 10, 80, 75, 3, 4, 10, 0, 0, 8, 0, 0, 59, 75,
            -116, 69, 112, -99, 33, 43, 5, 0, 0, 0, 5, 0, 0, 0, 7, 0, 0, 0,
            101, 110, 116, 114, 121, 45, 49, 101, 110, 116, 114, 121, 80, 75,
            3, 4, 10, 0, 0, 8, 0, 0, 59, 75, -116, 69, 112, -99, 33, 43, 5, 0,
            0, 0, 5, 0, 0, 0, 7, 0, 0, 0, 101, 110, 116, 114, 121, 45, 50, 101,
            110, 116, 114, 121, 80, 75, 3, 4, 10, 0, 0, 8, 0, 0, 59, 75, -116,
            69, 112, -99, 33, 43, 5, 0, 0, 0, 5, 0, 0, 0, 7, 0, 0, 0, 101, 110,
            116, 114, 121, 45, 51, 101, 110, 116, 114, 121, 80, 75, 1, 2, 10,
            0, 10, 0, 0, 8, 0, 0, 59, 75, -116, 69, -34, -12, 123, 2, 123, 0,
            0, 0, 123, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 77, 68, 53, 83, 85, 77, 80, 75, 1, 2, 10, 0, 10, 0, 0, 8, 0,
            0, 59, 75, -116, 69, 112, -99, 33, 43, 5, 0, 0, 0, 5, 0, 0, 0, 7,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -97, 0, 0, 0, 101, 110, 116,
            114, 121, 45, 49, 80, 75, 1, 2, 10, 0, 10, 0, 0, 8, 0, 0, 59, 75,
            -116, 69, 112, -99, 33, 43, 5, 0, 0, 0, 5, 0, 0, 0, 7, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, -55, 0, 0, 0, 101, 110, 116, 114, 121,
            45, 50, 80, 75, 1, 2, 10, 0, 10, 0, 0, 8, 0, 0, 59, 75, -116, 69,
            112, -99, 33, 43, 5, 0, 0, 0, 5, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, -13, 0, 0, 0, 101, 110, 116, 114, 121, 45, 51,
            80, 75, 5, 6, 0, 0, 0, 0, 4, 0, 4, 0, -45, 0, 0, 0, 29, 1, 0, 0, 0,
            0 };

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(FileSystemStorageSystemProvider.class)
            .addClass(ZipContainerProvider.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject @Named("org.dcm4chee.storage.zip")
    ContainerProvider provider;

    @Rule
    public TransientDirectory dir = new TransientDirectory(DIR_PATH);

    Container container;
    StorageContext storageCtx; 
    RetrieveContext retrieveCtx;

    @Before
    public void setup() throws Exception {
        container = new Container();
        container.setChecksumEntry("MD5SUM");
        provider.init(container);
        storageCtx = new StorageContext();
        storageCtx.setArchiverProvider(provider);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setContainerProvider(provider);
        retrieveCtx.setDigestAlgorithm("MD5");
    }

    @Test
    public void testWriteEntriesTo() throws Exception {
        Path srcEntryPath = createFile(ENTRY, ENTRY_FILE);
        Path targetZipPath = dir.getPath().resolve(NAME);
        try ( OutputStream out = Files.newOutputStream(targetZipPath)) {
             provider.writeEntriesTo(storageCtx, makeEntries(srcEntryPath), out);
        }
        assertEquals(ZIP.length, Files.size(targetZipPath));
        try (
           ZipInputStream expectedZip = new ZipInputStream(new ByteArrayInputStream(ZIP));
           ZipInputStream actualZip = new ZipInputStream(Files.newInputStream(targetZipPath))
        ) {
           assertZIPEquals(expectedZip, actualZip);
        }
    }

    private static void assertZIPEquals(ZipInputStream expectedZip,
            ZipInputStream actualZip) throws IOException {
        ZipEntry expected;
        ZipEntry actual;
        while ((expected = expectedZip.getNextEntry()) != null) {
            actual = actualZip.getNextEntry();
            assertNotNull(actual);
            assertZipEntryEquals(expected, actual);
        }
    }

    private static void assertZipEntryEquals(ZipEntry expected, ZipEntry actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getCrc(), actual.getCrc());
        assertEquals(expected.getSize(), actual.getSize());
    }

    @Test
    public void testSeekEntry() throws Exception {
        Path targetFilePath = dir.getPath().resolve(ENTRY_NAME);
        try (InputStream in = new ByteArrayInputStream(ZIP)) {
            Files.copy(provider.seekEntry(
                    retrieveCtx, NAME, ENTRY_NAME, in),
                    targetFilePath);
        }
        assertArrayEquals(ENTRY, readFile(targetFilePath));
    }

    @Test
    public void testExtractEntries() throws Exception {
        final ArrayList<String> entryNames = new ArrayList<String>();
        ExtractTask extractTask = new ExtractTask() {

            @Override
            public void copyStream(String entryName, InputStream in)
                    throws IOException {
                Path path = dir.getPath().resolve(entryName);
                Files.copy(in, path);
            }

            @Override
            public void entryExtracted(String entryName) {
                try {
                    entryNames.add(entryName);
                    assertArrayEquals(ENTRY,
                            readFile(dir.getPath().resolve(entryName)));
                } catch (IOException e) {
                    fail(e.getMessage());
                }
            }

            @Override
            public void finished() {
            }

            @Override
            public void exception(IOException ex) {
            }

            @Override
            public Path getFile(String entryName) throws IOException,
                    InterruptedException {
                return null;
            }
        };

        try (InputStream in = new ByteArrayInputStream(ZIP)) {
            provider.extractEntries(retrieveCtx, NAME, extractTask, in);
        }
        assertEquals(Arrays.asList(ENTRY_NAMES), entryNames);
    }

    private Path createFile(byte[] b, String name) throws IOException {
        Path path = dir.getPath().resolve(name);
        try ( OutputStream out = Files.newOutputStream(path) ) {
            out.write(b);
        }
        return path;
    }

    private byte[] readFile(Path path) throws IOException {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream((int) Files.size(path));
        Files.copy(path, out);
        return out.toByteArray();
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
