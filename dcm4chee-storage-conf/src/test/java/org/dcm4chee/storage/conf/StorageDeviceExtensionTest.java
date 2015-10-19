/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contentsOfthis file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copyOfthe License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is partOfdcm4che, an implementationOfDICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial DeveloperOfthe Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contentsOfthis file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisionsOfthe GPL or the LGPL are applicable instead
 * of those above. If you wish to allow useOfyour versionOfthis file only
 * under the termsOfeither the GPL or the LGPL, and not to allow others to
 * use your versionOfthis file under the termsOfthe MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your versionOfthis file under
 * the termsOfany oneOfthe MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.storage.conf;

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.internal.ExtendedDicomConfiguration;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4che3.net.Device;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class StorageDeviceExtensionTest {

    private static final String DEVICE_NAME = "dcm4chee-storage-test";

    ExtendedDicomConfiguration config;

    @Before
    public void setUp() throws Exception {

        DicomConfigurationBuilder builder;
        if (System.getProperty("ldap") != null) {
            Properties env = new Properties();
            try (InputStream inStream = Thread.currentThread()
                    .getContextClassLoader().getResourceAsStream("ldap.properties")) {
                env.load(inStream);
            }
            builder = DicomConfigurationBuilder.newLdapConfigurationBuilder(env);
        } else {
            builder = DicomConfigurationBuilder.newJsonConfigurationBuilder("target/config.json");
        }
        builder.registerDeviceExtension(StorageDeviceExtension.class);
        config = builder.cache(false).build();
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        config.close();
    }

    @Test
    public void test() throws Exception {
        Device dev1 = new Device(DEVICE_NAME);
        StorageDeviceExtension ext1 = createStorageDeviceExtension();
        dev1.addDeviceExtension(ext1);
        config.persist(dev1);
        Device dev2 = config.findDevice(DEVICE_NAME);
        StorageDeviceExtension ext2 =
                dev2.getDeviceExtension(StorageDeviceExtension.class);
        assertEquals(ext1, ext2);
    }

    private void cleanUp() throws Exception {
        try {
            config.removeDevice(DEVICE_NAME);
        }  catch (ConfigurationNotFoundException e) {}
    }

    private StorageDeviceExtension createStorageDeviceExtension() {
        StorageDeviceExtension ext = new StorageDeviceExtension();
        StorageSystemGroup primaryStorage = new StorageSystemGroup();
        primaryStorage.setGroupID("Primary Storage");
        primaryStorage.setBaseStorageAccessTime(0);
        Map<String, String> offsets = new TreeMap<String, String>();
        offsets.put("siteA", "0");
        offsets.put("siteB", "1");
        primaryStorage.setStorageAccessTimeOffsetMap(offsets);
        primaryStorage.setDescription("test");
        primaryStorage.setInstalled(true);
        StorageSystem fs1 = newFileSystem("fs1", "fs2", true);
        StorageSystem fs2 = newFileSystem("fs2", "fs3", false);
        StorageSystem fs3 = newFileSystem("fs3", "fs4", false);
        StorageSystem fs4 = newFileSystem("fs4", "fs1", false);
        primaryStorage.addStorageSystem(fs1);
        primaryStorage.addStorageSystem(fs2);
        primaryStorage.addStorageSystem(fs3);
        primaryStorage.addStorageSystem(fs4);
        primaryStorage.setParallelism(2);
        ext.addStorageSystemGroup(primaryStorage);

        StorageSystemGroup secondaryStorage = new StorageSystemGroup();
        secondaryStorage.setGroupID("Secondary Storage");
        secondaryStorage.setBaseStorageAccessTime(2);
        secondaryStorage.setDescription("test");
        secondaryStorage.setInstalled(true);

        Container container = new Container();
        container.setProviderName("org.dcm4chee.storage.zip");
        container.setChecksumEntry("MD5SUM");
        container.setCompress(false);
        secondaryStorage.setContainer(container);

        FileCache fileCache = new FileCache();
        fileCache.setProviderName("org.dcm4che3.filecache");
        fileCache.setFileCacheRootDirectory("/var/local/dcm4chee-arc/cache");
        fileCache.setJournalRootDirectory("/var/local/dcm4chee-arc/cache-journal");
        fileCache.setCacheAlgorithm(FileCache.Algorithm.LRU);
        fileCache.setMinFreeSpace("64MiB");
        secondaryStorage.setFileCache(fileCache);

        StorageSystem aws_s3 = new StorageSystem();
        aws_s3.setProviderName("org.jclouds.aws");
        aws_s3.setStorageSystemID("aws-s3");
        aws_s3.setStorageSystemPath("dcm4chee-arc");
        aws_s3.setAvailability(Availability.NEARLINE);
        aws_s3.setStorageSystemStatus(StorageSystemStatus.OK);
        aws_s3.setDescription("test");
        aws_s3.setCacheOnStore(true);
        secondaryStorage.addStorageSystem(aws_s3);
        ext.addStorageSystemGroup(secondaryStorage);

        Archiver archiver = new Archiver();
        archiver.setMaxRetries(48);
        archiver.setRetryInterval(7200);
        archiver.setVerifyContainer(false);
        ext.setArchiver(archiver);

        return ext;
    }

    private StorageSystem newFileSystem(String systemID, String nextSystemID,
            boolean readOnly) {
        StorageSystem fs = new StorageSystem();
        fs.setProviderName("org.dcm4chee.storage.filesystem");
        fs.setStorageSystemID(systemID);
        fs.setStorageSystemPath("/var/local/dcm4chee-arc/" + systemID);
        fs.setNextStorageSystemID(nextSystemID);
        fs.setReadOnly(readOnly);
        fs.setMinFreeSpace("64MiB");
        fs.setMountCheckFile("NO_MOUNT");
        fs.setInstalled(true);
        fs.setStorageSystemStatus(StorageSystemStatus.OK);
        fs.setDescription("test");
        return fs;
    }

    private void assertEquals(StorageDeviceExtension expected,
            StorageDeviceExtension actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getStorageSystemGroupIDs(),
                actual.getStorageSystemGroupIDs());
        for (String groupID : expected.getStorageSystemGroupIDs()) {
            assertEquals(expected.getStorageSystemGroup(groupID),
                    actual.getStorageSystemGroup(groupID));
        }

        assertEquals(expected.getArchiver(), actual.getArchiver());
    }

    private void assertEquals(StorageSystemGroup expected,
            StorageSystemGroup actual) {
        Assert.assertEquals(expected.getBaseStorageAccessTime(),
                actual.getBaseStorageAccessTime());
        for (String accessID : expected.getStorageAccessTimeOffsetMap().keySet()) {
            Assert.assertEquals(expected.getStorageAccessTimeOffsetMap().get(accessID),
                    actual.getStorageAccessTimeOffsetMap().get(accessID));
        }
        Assert.assertEquals(expected.getInstalled(), actual.getInstalled());
        assertEquals(expected.getContainer(), actual.getContainer());
        assertEquals(expected.getFileCache(), actual.getFileCache());
        Assert.assertEquals(expected.getStorageSystemIDs(),
                actual.getStorageSystemIDs());
        for (String systemID : expected.getStorageSystemIDs()) {
            assertEquals(expected.getStorageSystem(systemID),
                    actual.getStorageSystem(systemID));
        }
        Assert.assertArrayEquals(expected.getActiveStorageSystemIDs(),
                actual.getActiveStorageSystemIDs());
        Assert.assertEquals(expected.getNextStorageSystemID(),
                actual.getNextStorageSystemID());
        Assert.assertEquals(expected.getParallelism(),
                actual.getParallelism());
    }

    private void assertEquals(FileCache expected, FileCache actual) {
        if (expected == null) {
            Assert.assertNull(actual);
            return;
        }
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getProviderName(), actual.getProviderName());
        Assert.assertEquals(expected.getFileCacheRootDirectory(), actual.getFileCacheRootDirectory());
        Assert.assertEquals(expected.getJournalRootDirectory(), actual.getJournalRootDirectory());
        Assert.assertEquals(expected.getJournalFileName(), actual.getJournalFileName());
        Assert.assertEquals(expected.getJournalDirectoryName(), actual.getJournalDirectoryName());
        Assert.assertEquals(expected.getJournalFileNamePattern(), actual.getJournalFileNamePattern());
        Assert.assertEquals(expected.getOrphanedFileName(), actual.getOrphanedFileName());
        Assert.assertEquals(expected.getJournalMaxEntries(), actual.getJournalMaxEntries());
        Assert.assertEquals(expected.getCacheAlgorithm(), actual.getCacheAlgorithm());
        Assert.assertEquals(expected.getMinFreeSpace(), actual.getMinFreeSpace());
    }

    private void assertEquals(Container expected, Container actual) {
        if (expected == null) {
            Assert.assertNull(actual);
            return;
        }
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getProviderName(), actual.getProviderName());
        Assert.assertEquals(expected.getChecksumEntry(), actual.getChecksumEntry());
        Assert.assertEquals(expected.isCompress(), actual.isCompress());
    }

    private void assertEquals(StorageSystem expected, StorageSystem actual) {
        Assert.assertEquals(expected.getProviderName(), actual.getProviderName());
        Assert.assertEquals(expected.getInstalled(), actual.getInstalled());
        Assert.assertEquals(expected.getStorageSystemPath(), actual.getStorageSystemPath());
        Assert.assertEquals(expected.isReadOnly(), actual.isReadOnly());
        Assert.assertEquals(expected.isCacheOnStore(), actual.isCacheOnStore());
        Assert.assertEquals(expected.getAvailability(), actual.getAvailability());
        Assert.assertEquals(expected.getMinFreeSpace(), actual.getMinFreeSpace());
        Assert.assertEquals(expected.getMountCheckFile(), actual.getMountCheckFile());
    }

    private void assertEquals(Archiver expected, Archiver actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getMaxRetries(), actual.getMaxRetries());
        Assert.assertEquals(expected.getRetryInterval(), actual.getRetryInterval());
        Assert.assertEquals(expected.isVerifyContainer(), actual.isVerifyContainer());
    }

}
