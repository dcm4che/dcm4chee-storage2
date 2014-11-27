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

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.core.Configuration;
import org.dcm4che3.conf.core.normalization.DefaultsFilterDecorator;
import org.dcm4che3.conf.core.storage.CachedRootNodeConfiguration;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
import org.dcm4che3.conf.ldap.LdapConfigurationStorage;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DeviceExtension;
import org.dcm4che3.util.SafeClose;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StorageDeviceExtensionTest {

    private static final String DEVICE_NAME = "dcm4chee-storage-test";

    DicomConfiguration config;

    @Before
    public void setUp() throws Exception {


        Class[] deviceExtensionClasses = {StorageDeviceExtension.class};
        Class[] aeExtensionClasses = {};
        Class[] hl7AppExtensionClasses = {};

        List deviceExtensionClassList = Arrays.<Class<DeviceExtension>>asList(deviceExtensionClasses);
        List aeExtensionClassList = Arrays.<Class<AEExtension>>asList(aeExtensionClasses);

        List<Class<?>> allExtensionClasses = new ArrayList<>();
        allExtensionClasses.addAll(deviceExtensionClassList);
        allExtensionClasses.addAll(aeExtensionClassList);

        Configuration storage;

        if (System.getProperty("ldap") != null) {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.provider.url", "ldap://localhost:389/dc=example,dc=com");
            env.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
            env.put("java.naming.ldap.attributes.binary", "dicomVendorData");
            env.put("java.naming.security.principal", "cn=Directory Manager ");
            env.put("java.naming.security.credentials", "1");

            storage = new DefaultsFilterDecorator(
                    new CachedRootNodeConfiguration(
                            new LdapConfigurationStorage(env, allExtensionClasses)
                    ));
        } else {
            storage = new SingleJsonFileConfigurationStorage("target/config.json");
        }

        config = new CommonDicomConfiguration(
                storage,
                deviceExtensionClassList,
                aeExtensionClassList
        );


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
        primaryStorage.setInstalled(true);
        StorageSystem fs1 = newFileSystem("fs1", "fs2", true);
        StorageSystem fs2 = newFileSystem("fs2", "fs3", false);
        StorageSystem fs3 = newFileSystem("fs3", "fs4", false);
        StorageSystem fs4 = newFileSystem("fs4", null, false);
        primaryStorage.addStorageSystem(fs1);
        primaryStorage.addStorageSystem(fs2);
        primaryStorage.addStorageSystem(fs3);
        primaryStorage.addStorageSystem(fs4);
        primaryStorage.activate(fs2, true);
        primaryStorage.activate(fs3, true);
        ext.addStorageSystemGroup(primaryStorage);

        StorageSystemGroup secondaryStorage = new StorageSystemGroup();
        secondaryStorage.setGroupID("Secondary Storage");
        secondaryStorage.setInstalled(true);

        Archiver archiver = new Archiver();
        archiver.setProviderName("org.apache.commons.compress.archivers.tar");
        secondaryStorage.setArchiver(archiver);

        FileCache fileCache = new FileCache();
        fileCache.setProviderName("org.dcm4che3.filecache");
        secondaryStorage.setFileCache(fileCache);

        StorageSystem aws_s3 = new StorageSystem();
        aws_s3.setProviderName("org.jclouds.aws");
        aws_s3.setStorageSystemID("aws-s3");
        aws_s3.setStorageSystemPath("dcm4chee-arc");
        aws_s3.setStorageAccessTime(2);
        aws_s3.setAvailability(Availability.NEARLINE);
        aws_s3.setStorageSystemStatus(StorageSystemStatus.OK);
        secondaryStorage.addStorageSystem(aws_s3);
        secondaryStorage.activate(aws_s3, false);
        ext.addStorageSystemGroup(secondaryStorage);

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
        fs.setStorageAccessTime(0);
        fs.setMinFreeSpace("64MiB");
        fs.setMountCheckFile("NO_MOUNT");
        fs.setInstalled(true);
        fs.setStorageSystemStatus(StorageSystemStatus.OK);
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
    }

    private void assertEquals(StorageSystemGroup expected,
            StorageSystemGroup actual) {
        Assert.assertEquals(expected.getInstalled(), actual.getInstalled());
        assertEquals(expected.getArchiver(), actual.getArchiver());
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
    }

    private void assertEquals(FileCache expected, FileCache actual) {
        if (expected == null) {
            Assert.assertNull(actual);
            return;
        }
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getProviderName(), actual.getProviderName());
    }

    private void assertEquals(Archiver expected, Archiver actual) {
        if (expected == null) {
            Assert.assertNull(actual);
            return;
        }
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getProviderName(), actual.getProviderName());
    }

    private void assertEquals(StorageSystem expected, StorageSystem actual) {
        Assert.assertEquals(expected.getProviderName(), actual.getProviderName());
        Assert.assertEquals(expected.getInstalled(), actual.getInstalled());
        Assert.assertEquals(expected.getStorageSystemPath(), actual.getStorageSystemPath());
        Assert.assertEquals(expected.isReadOnly(), actual.isReadOnly());
        Assert.assertEquals(expected.getStorageAccessTime(), actual.getStorageAccessTime());
        Assert.assertEquals(expected.getAvailability(), actual.getAvailability());
        Assert.assertEquals(expected.getMinFreeSpace(), actual.getMinFreeSpace());
        Assert.assertEquals(expected.getMountCheckFile(), actual.getMountCheckFile());
    }

}
