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
 * Portions created by the Initial Developer are Copyright (C) 2012-2015
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

package org.dcm4chee.storage.test.unit.cifs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.StorageDevice;
import org.dcm4chee.storage.cifs.CifsStorageSystemProvider;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.encrypt.StorageSystemProviderEncryptDecorator;
import org.dcm4chee.storage.spi.StorageSystemProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@RunWith(Arquillian.class)
public class CifsSystemStorageSystemProviderTest {

    private static final String ID1 = "a/b/c";
    private static final String ID2 = "x/y/z";
    private static final Path LOCAL_FILE = Paths.get("target/test-storage/cifs/test");
    private static final String DATA = "test";

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap
                .create(JavaArchive.class)
                .addClass(CifsStorageSystemProvider.class)
                .addClass(StorageSystemProviderEncryptDecorator.class)
                .addAsManifestResource(
                        new StringAsset(
                                "<decorators><class>org.dcm4chee.storage.encrypt.StorageSystemProviderEncryptDecorator</class></decorators>"),
                        "beans.xml");
    }

    @Inject
    @Named("org.dcm4chee.storage.cifs")
    private StorageSystemProvider provider;

    @Produces @StorageDevice
    static Device device = new Device("test");

    private SmbFile baseDir;
    private SmbFile file1;
    private SmbFile file2;

    private StorageDeviceExtension ext;
    private StorageSystemGroup cifsGroup;
    private StorageSystem cifs;
    private StorageContext storageCtx;
    private RetrieveContext retrieveCtx;

    @Before
    public void setup() throws IOException, URISyntaxException {
        // Example: smb://mydomain;skroetsch:secret@localhost/shared/cifs-test/
        String prop = System.getProperty("smburl");
        Assume.assumeNotNull(prop);

        baseDir = new SmbFile(prop);
        URL url = baseDir.getURL();

        cifs = newStorageSystem(url);

        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        cifsGroup = new StorageSystemGroup();
        cifsGroup.setGroupID("cifs");
        ext.addStorageSystemGroup(cifsGroup);
        cifs.setStorageSystemGroup(cifsGroup);
        provider.init(cifs);
        storageCtx = new StorageContext();
        storageCtx.setStorageSystem(cifs);
        storageCtx.setStorageSystemProvider(provider);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setStorageSystem(cifs);
        retrieveCtx.setStorageSystemProvider(provider);

        if (!Files.exists(LOCAL_FILE)) {
            Files.createDirectories(LOCAL_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(LOCAL_FILE,
                    StandardCharsets.UTF_8)) {
                writer.write(DATA);
            }
        }

        file2 = new SmbFile(baseDir, ID2);
        if (file2.exists())
            file2.delete();
        file1 = new SmbFile(baseDir, ID1);
        if (!file1.exists()) {
            SmbFile dir = new SmbFile(file1.getParent());
            dir.mkdirs();
            try (Writer writer = new OutputStreamWriter(file1.getOutputStream())) {
                writer.write(DATA);
            }
        }
    }

    private StorageSystem newStorageSystem(URL url) {
        StorageSystem cifs = new StorageSystem();
        cifs.setStorageSystemHostname(url.getHost());
        cifs.setStorageSystemPort(url.getPort());
        cifs.setStorageSystemPath(url.getPath());
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(
                url.getUserInfo());
        cifs.setStorageSystemIdentity(auth.getUsername());
        cifs.setStorageSystemCredential(auth.getPassword());
        cifs.setStorageSystemDomain(auth.getDomain());
        return cifs;
    }

    @After
    public void teardown() {
        device.removeDeviceExtension(ext);
        ext = null;
        cifsGroup = null;
        cifs = null;
    }

    @Test
    public void testOpenOutputStream() throws IOException {
        Assert.assertFalse(file2.exists());
        try (OutputStream out = provider.openOutputStream(storageCtx, ID2)) {
            Files.copy(LOCAL_FILE, out);
        }
        Assert.assertTrue(file2.exists());
        Assert.assertEquals(Files.size(LOCAL_FILE), storageCtx.getFileSize());
    }

    @Test(expected = ObjectAlreadyExistsException.class)
    public void testOpenOutputStreamThrowsException() throws IOException {
        provider.openOutputStream(storageCtx, ID1).close();
    }

    @Test
    public void testStoreFile() throws IOException {
        Assert.assertFalse(file2.exists());
        provider.storeFile(storageCtx, LOCAL_FILE, ID2);
        Assert.assertTrue(file2.exists());
        Assert.assertEquals(Files.size(LOCAL_FILE), storageCtx.getFileSize());
    }

    @Test(expected = ObjectAlreadyExistsException.class)
    public void testStoreFileThrowsException() throws IOException {
        provider.storeFile(storageCtx, LOCAL_FILE, ID1);
    }

    @Test
    public void testCopyInputStream() throws IOException {
        try (InputStream in = Files.newInputStream(LOCAL_FILE, StandardOpenOption.READ)) {
            provider.copyInputStream(storageCtx, in, ID2);
        }
        Assert.assertEquals(Files.size(LOCAL_FILE), storageCtx.getFileSize());
    }

    @Test
    public void testMoveFile() throws IOException {
        Assert.assertTrue(Files.exists(LOCAL_FILE));
        Assert.assertFalse(file2.exists());
        provider.moveFile(storageCtx, LOCAL_FILE, ID2);
        Assert.assertTrue(file2.exists());
        Assert.assertFalse(Files.exists(LOCAL_FILE));
    }

    @Test
    public void testDeleteObject() throws IOException {
        Assert.assertTrue(file1.exists());
        provider.deleteObject(storageCtx, ID1);
        Assert.assertFalse(file1.exists());
        SmbFile dir = new SmbFile(file1.getParent());
        Assert.assertFalse(dir.exists());
    }

    @Test(expected = ObjectNotFoundException.class)
    public void testDeleteObjectThrowsException() throws IOException {
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
}
