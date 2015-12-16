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

package org.dcm4chee.storage.test.unit.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageDevice;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.encrypt.StorageSystemProviderEncryptDecorator;
import org.dcm4chee.storage.sftp.SftpStorageSystemProvider;
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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@RunWith(Arquillian.class)
public class SftpSystemStorageSystemProviderTest {

    private static final String ID1 = "a/b/c";
    private static final String ID2 = "x/y/z";
    private static final Path LOCAL_FILE = Paths.get("target/test-storage/sftp/test");
    private static final String DATA = "test";

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap
                .create(JavaArchive.class)
                .addClass(SftpStorageSystemProvider.class)
                .addClass(StorageSystemProviderEncryptDecorator.class)
                .addAsManifestResource(
                        new StringAsset(
                                "<decorators><class>org.dcm4chee.storage.encrypt.StorageSystemProviderEncryptDecorator</class></decorators>"),
                        "beans.xml");
    }

    @Inject
    @Named("org.dcm4chee.storage.sftp")
    private StorageSystemProvider provider;

    @Produces @StorageDevice
    static Device device = new Device("test");

    private String file1;
    private String file2;

    private StorageDeviceExtension ext;
    private StorageSystemGroup sftpGroup;
    private StorageSystem sftp;
    private StorageContext storageCtx;
    private RetrieveContext retrieveCtx;
    private Session session;
    private ChannelSftp channel;

    @Before
    public void setup() throws IOException, URISyntaxException, JSchException,
            SftpException {
        // Example: sftp://bob:secret@localhost/sftp-test
        String prop = System.getProperty("sftpurl");
        Assume.assumeNotNull(prop);

        URI url = new URI(prop);

        sftp = newStorageSystem(url);

        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        sftpGroup = new StorageSystemGroup();
        sftpGroup.setGroupID("cifs");
        ext.addStorageSystemGroup(sftpGroup);
        sftp.setStorageSystemGroup(sftpGroup);
        provider.init(sftp);
        storageCtx = new StorageContext();
        storageCtx.setStorageSystem(sftp);
        storageCtx.setStorageSystemProvider(provider);
        retrieveCtx = new RetrieveContext();
        retrieveCtx.setStorageSystem(sftp);
        retrieveCtx.setStorageSystemProvider(provider);

        JSch jsch = new JSch();
        int port = sftp.getStorageSystemPort();
        session = jsch.getSession(sftp.getStorageSystemIdentity(),
                sftp.getStorageSystemHostname(), port != -1 ? port : 22);
        session.setPassword(sftp.getStorageSystemCredential());
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();
        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        file2 = resolvePath(ID2);
        if (exists(file2))
            delete(file2);

        file1 = resolvePath(ID1);
        if (!exists(file1)) {
            mkdirs(getParentDir(file1));
            try (Writer writer = new OutputStreamWriter(openOutputStream(file1))) {
                writer.write(DATA);
            }
        }

        if (!Files.exists(LOCAL_FILE)) {
            Files.createDirectories(LOCAL_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(LOCAL_FILE,
                    StandardCharsets.UTF_8)) {
                writer.write(DATA);
            }
        }
    }

    private void mkdirs(String dir) throws SftpException {
        String parent = getParentDir(dir);
        if (parent == null)
            return;
        if (!exists(parent))
            mkdirs(parent);
        channel.mkdir(dir);
    }

    private String getParentDir(String path) {
        int pos = path.lastIndexOf('/');
        if (pos == -1)
            return null;
        String dir = path.substring(0, pos);
        return dir.isEmpty() ? null : dir;
    }

    private boolean exists(String path) throws SftpException {
        try {
            channel.stat(path);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE)
                return false;
            throw e;
        }
    }

    private OutputStream openOutputStream(String dest) throws SftpException {
        return channel.put(dest);
    }

    private void delete(String path) throws SftpException {
        channel.rm(path);
    }

    private String resolvePath(String name) {
        StringBuilder sb = new StringBuilder(sftp.getStorageSystemPath());
        if (sb.charAt(sb.length() - 1) != '/')
            sb.append('/');
        sb.append(name);
        return sb.toString();
    }

    private StorageSystem newStorageSystem(URI url) {
        StorageSystem sftp = new StorageSystem();
        sftp.setStorageSystemHostname(url.getHost());
        sftp.setStorageSystemPort(url.getPort());
        sftp.setStorageSystemPath(url.getPath());
        String user = url.getUserInfo();
        if (user != null) {
            int colon = user.indexOf(':');
            if (colon != -1) {
                String pass = user.substring(colon + 1);
                sftp.setStorageSystemCredential(pass);
                user = user.substring(0, colon);
            }
            sftp.setStorageSystemIdentity(user);
        }
        return sftp;
    }

    @After
    public void teardown() {
        channel.disconnect();
        session.disconnect();
        device.removeDeviceExtension(ext);
        ext = null;
        sftpGroup = null;
        sftp = null;
    }

    @Test
    public void testOpenOutputStream() throws IOException, SftpException {
        Assert.assertFalse(exists(file2));
        try (OutputStream out = provider.openOutputStream(storageCtx, ID2)) {
            Files.copy(LOCAL_FILE, out);
        }
        Assert.assertTrue(exists(file2));
         Assert.assertEquals(Files.size(LOCAL_FILE),
         storageCtx.getFileSize());
    }

    @Test(expected = ObjectAlreadyExistsException.class)
    public void testOpenOutputStreamThrowsException() throws IOException {
        provider.openOutputStream(storageCtx, ID1).close();
    }

    @Test
    public void testStoreFile() throws IOException, SftpException {
        Assert.assertFalse(exists(file2));
        provider.storeFile(storageCtx, LOCAL_FILE, ID2);
        Assert.assertTrue(exists(file2));
         Assert.assertEquals(Files.size(LOCAL_FILE),
         storageCtx.getFileSize());
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
         Assert.assertEquals(Files.size(LOCAL_FILE),
         storageCtx.getFileSize());
    }

    @Test
    public void testMoveFile() throws IOException, SftpException {
        Assert.assertTrue(Files.exists(LOCAL_FILE));
        Assert.assertFalse(exists(file2));
        provider.moveFile(storageCtx, LOCAL_FILE, ID2);
        Assert.assertTrue(exists(file2));
        Assert.assertFalse(Files.exists(LOCAL_FILE));
    }

    @Test
    public void testDeleteObject() throws IOException, SftpException {
        Assert.assertTrue(exists(file1));
        provider.deleteObject(storageCtx, ID1);
        Assert.assertFalse(exists(file1));
        Assert.assertFalse(exists(getParentDir(file1)));
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
