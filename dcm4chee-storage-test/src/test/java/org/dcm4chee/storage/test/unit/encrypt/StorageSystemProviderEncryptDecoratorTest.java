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

package org.dcm4chee.storage.test.unit.encrypt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.SecretKey;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.dcm4che3.net.Device;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.StorageDevice;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.encrypt.BlockCipherInputStream;
import org.dcm4chee.storage.encrypt.BlockCipherOutputStream;
import org.dcm4chee.storage.encrypt.StorageSystemProviderEncryptDecorator;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@RunWith(Arquillian.class)
public class StorageSystemProviderEncryptDecoratorTest {

    private static final String ID1 = "a/b/c";
    private static final String ID2 = "x/y/z";
    private static final String FS_PATH = "target/test-storage/encrypt";
    private static final Path DIR = Paths.get(FS_PATH);
    private static final Path FILE1 = DIR.resolve(ID1);
    private static final Path FILE2 = DIR.resolve(ID2);
    private static final String KEYSTORE_URL = Paths.get("src/test/data/key.jks").toUri()
            .toString();
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final String KEYSTORE_TYPE = "jceks";
    private static final String KEY_ALIAS = "test";
    private static final byte[] TEST_DATA = { 't', 'e', 's', 't' };

    private static SecretKey secretKey;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap
                .create(JavaArchive.class)
                .addClass(FileSystemStorageSystemProvider.class)
                .addClass(StorageSystemProviderEncryptDecorator.class)
                .addAsManifestResource(
                        new StringAsset(
                                "<decorators><class>org.dcm4chee.storage.encrypt.StorageSystemProviderEncryptDecorator</class></decorators>"),
                        "beans.xml");
    }

    @Inject
    @Named("org.dcm4chee.storage.filesystem")
    StorageSystemProvider provider;

    @Produces @StorageDevice
    static Device device = new Device("test");

    StorageDeviceExtension ext;
    StorageSystemGroup fsGroup;
    StorageSystem fs;
    StorageContext storageCtx;
    RetrieveContext retrieveCtx;

    @Before
    public void setup() throws IOException, InterruptedException {
        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        device.setKeyStoreURL(KEYSTORE_URL);
        device.setKeyStorePin(KEYSTORE_PASSWORD);
        device.setKeyStoreKeyPin(KEYSTORE_PASSWORD);
        device.setKeyStoreType(KEYSTORE_TYPE);
        fsGroup = new StorageSystemGroup();
        fsGroup.setGroupID("fs");
        ext.addStorageSystemGroup(fsGroup);
        fs = new StorageSystem();
        fs.setStorageSystemID("fs");
        fs.setStorageSystemPath(FS_PATH);
        fs.setStorageSystemStatus(StorageSystemStatus.OK);
        fs.setEncryptionKeyAlias(KEY_ALIAS);
        fs.setStorageSystemGroup(fsGroup);
        provider.init(fs);
        storageCtx = new StorageContext();
        storageCtx.setStorageSystemProvider(provider);
        storageCtx.setStorageSystem(fs);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setStorageSystemProvider(provider);
        retrieveCtx.setStorageSystem(fs);
        Files.deleteIfExists(FILE2);
        if (!Files.exists(FILE1)) {
            Files.createDirectories(FILE1.getParent());
            try (OutputStream out = Files.newOutputStream(FILE1,
                    StandardOpenOption.CREATE)) {
                out.write(TEST_DATA);
            }
        }
    }

    @After
    public void teardown() {
        device.removeDeviceExtension(ext);
        ext = null;
        fsGroup = null;
        fs = null;
    }

    @BeforeClass
    public static void readSecretKey() throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream in = StreamUtils.openFileOrURL(KEYSTORE_URL)) {
            ks.load(in, KEYSTORE_PASSWORD.toCharArray());
            secretKey = (SecretKey) ks.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    @Test
    public void testOpenOutputStream() throws Exception {
        Assert.assertFalse(Files.exists(FILE2));
        try (OutputStream out = provider.openOutputStream(storageCtx, ID2)) {
            Files.copy(FILE1, out);
        }
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertArrayEquals(TEST_DATA, decryptToByteArray(FILE2));
    }

    @Test
    public void testStoreFile() throws Exception {
        Assert.assertFalse(Files.exists(FILE2));
        provider.storeFile(storageCtx, FILE1, ID2);
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertArrayEquals(TEST_DATA, decryptToByteArray(FILE2));
    }

    @Test
    public void testMoveFile() throws Exception {
        Assert.assertTrue(Files.exists(FILE1));
        Assert.assertFalse(Files.exists(FILE2));
        provider.moveFile(storageCtx, FILE1, ID2);
        Assert.assertTrue(Files.exists(FILE2));
        Assert.assertFalse(Files.exists(FILE1));
        Assert.assertArrayEquals(TEST_DATA, decryptToByteArray(FILE2));
    }

    @Test
    public void testOpenInputStream() throws Exception {
        Assert.assertFalse(Files.exists(FILE2));
        Files.createDirectories(FILE2.getParent());
        try (OutputStream encrypt = new BlockCipherOutputStream(Files.newOutputStream(
                FILE2, StandardOpenOption.CREATE), secretKey)) {
            Files.copy(FILE1, encrypt);
        }

        try (InputStream decrypt = provider.openInputStream(retrieveCtx, ID2)) {
            Assert.assertArrayEquals(TEST_DATA, copyToByteArray(decrypt));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetFile() throws Exception {
        provider.getFile(retrieveCtx, ID1);
    }

    @Test
    public void testDeleteObject() throws Exception {
        Assert.assertTrue(Files.exists(FILE1));
        provider.deleteObject(storageCtx, ID1);
        Assert.assertFalse(Files.exists(FILE1));
    }

    private byte[] decryptToByteArray(Path encryptedFile) throws IOException {
        try (InputStream in = new BlockCipherInputStream(
                Files.newInputStream(encryptedFile), secretKey)) {
            return copyToByteArray(in);
        }
    }

    private byte[] copyToByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamUtils.copy(in, out);
        return out.toByteArray();
    }
}
