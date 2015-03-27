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

package org.dcm4chee.storage.test.unit.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.dcm4che3.net.Device;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class FileSystemStorageSystemProviderTest {

    private static final String ID1 = "a/b/c";
    private static final String ID2 = "x/y/z";
    private static final String FS_PATH = "target/test-storage/fs";
    private static final Path DIR = Paths.get(FS_PATH);
    private static final Path FILE1 = DIR.resolve(ID1);
    private static final Path FILE2 = DIR.resolve(ID2);

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(FileSystemStorageSystemProvider.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject @Named("org.dcm4chee.storage.filesystem")
    StorageSystemProvider provider;

    @Produces
    static Device device = new Device("test");
    

    StorageDeviceExtension ext;
    StorageSystemGroup fsGroup;
    StorageSystem fs;
    StorageContext storageCtx; 
    RetrieveContext retrieveCtx; 

    @Before
    public void setup() throws IOException {
        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        fsGroup = new StorageSystemGroup();
        fsGroup.setGroupID("fs");
        ext.addStorageSystemGroup(fsGroup);
        fs = new StorageSystem();
        fs.setStorageSystemID("fs");
        fs.setStorageSystemPath(FS_PATH);
        fs.setStorageSystemStatus(StorageSystemStatus.OK);
        fs.setStorageSystemGroup(fsGroup);
        provider.init(fs);
        storageCtx = new StorageContext();
        storageCtx.setStorageSystem(fs);
        storageCtx.setStorageSystemProvider(provider);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setStorageSystemProvider(provider);
        if (Files.exists(FILE2))
            Files.delete(FILE2);
        if (!Files.exists(FILE1)) {
            Files.createDirectories(FILE1.getParent());
            Files.createFile(FILE1);
        }
    }

    @After
    public void teardown() {
        device.removeDeviceExtension(ext);
        ext = null;
        fsGroup = null;
        fs = null;
    }

    @Test
    public void testOpenOutputStream() throws Exception {
        Assert.assertFalse(Files.exists(FILE2));
        provider.openOutputStream(storageCtx, ID2).close();
        Assert.assertTrue(Files.exists(FILE2));
    }

    @Test
    public void testOpenOutputStreamCalculateCheckSum() throws Exception {
        
        fsGroup.setDigestAlgorithm("SHA1");
        fsGroup.setCalculateCheckSumOnStore(true);
        
        Assert.assertFalse(Files.exists(FILE2));
        provider.openOutputStream(storageCtx, ID2).close();
        Assert.assertTrue(Files.exists(FILE2));
        
        Assert.assertNotNull(storageCtx.getDigest());
        Assert.assertEquals(TagUtils.toHexString(storageCtx.getDigest().digest()).length(), 40);
    }

    @Test
    public void testStoreFile() throws Exception {
        Assert.assertFalse(Files.exists(FILE2));
        provider.storeFile(storageCtx, FILE1, ID2);
        Assert.assertTrue(Files.exists(FILE2));
    }

    @Test
    public void testMoveFile() throws Exception {
        Assert.assertTrue(Files.exists(FILE1));
        Assert.assertFalse(Files.exists(FILE2));
        provider.moveFile(storageCtx, FILE1, ID2);
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertFalse(Files.exists(FILE1));
    }

    @Test
    public void testDeleteObject() throws Exception {
        Assert.assertTrue(Files.exists(FILE1));
        provider.deleteObject(storageCtx, ID1);
        Assert.assertFalse(Files.exists(FILE1));
    }

    @Test
    public void testOpenInputStream() throws Exception {
        provider.openInputStream(retrieveCtx, ID1).close();
    }

    @Test
    public void testGetFile() throws Exception {
        Assert.assertEquals(FILE1, provider.getFile(retrieveCtx, ID1));
    }

    @Test
    public void testCopyInputStreamCalculateCheckSum() throws IOException {
        
        fsGroup.setDigestAlgorithm("MD5");
        fsGroup.setCalculateCheckSumOnStore(true);
        try (InputStream in = Files.newInputStream(FILE1,
                StandardOpenOption.READ)) {
            provider.copyInputStream(storageCtx, in, ID2);
        }
        Assert.assertEquals(Files.size(FILE2), storageCtx.getFileSize());
        Assert.assertNotNull(storageCtx.getDigest());
        Assert.assertEquals(TagUtils.toHexString(storageCtx.getDigest().digest()).length(), 32);
    }
}
