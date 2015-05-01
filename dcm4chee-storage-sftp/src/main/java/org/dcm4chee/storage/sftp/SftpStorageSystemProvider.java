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

package org.dcm4chee.storage.sftp;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.spi.StorageSystemProvider;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpStatVFS;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@Named("org.dcm4chee.storage.sftp")
@Dependent
public class SftpStorageSystemProvider implements StorageSystemProvider {

    private static int DEFAULT_PORT = 22;

    private Session session;
    private StorageSystem storageSystem;

    @Override
    public void init(StorageSystem storageSystem) {
        this.storageSystem = storageSystem;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (session != null)
                    session.disconnect();
            }
        });
    }

    private synchronized ChannelSftp openChannel() throws IOException {
        if (session == null || !session.isConnected()) {
            JSch jsch = new JSch();
            String host = storageSystem.getStorageSystemHostname();
            int port = storageSystem.getStorageSystemPort();
            if (port == -1)
                port = DEFAULT_PORT;
            String user = storageSystem.getStorageSystemIdentity();
            try {
                session = jsch.getSession(user, host, port);
            } catch (JSchException e) {
                throw new IOException("Session create failed", e);
            }
            session.setPassword(storageSystem.getStorageSystemCredential());
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            try {
                session.connect();
            } catch (JSchException e) {
                throw new IOException("Session connect failed for server " + host + ":"
                        + port + " and " + " user " + user, e);
            }
        }

        ChannelSftp channel;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            throw new IOException("Open channel failed", e);
        }
        try {
            channel.connect();
        } catch (JSchException e) {
            throw new IOException("Connect channel failed", e);
        }
        return channel;
    }

    @Override
    public void checkWriteable() throws IOException {
    }

    @Override
    public long getUsableSpace() throws IOException {
        ChannelSftp channel = openChannel();
        try {
            SftpStatVFS stat = channel.statVFS(storageSystem.getStorageSystemPath());
            return stat.getAvailForNonRoot();
        } catch (SftpException e) {
            throw new IOException("Usable space check failed for path "
                    + storageSystem.getStorageSystemPath(), e);
        } finally {
            channel.disconnect();
        }
    }

    @Override
    public OutputStream openOutputStream(final StorageContext context, final String name)
            throws IOException {
        final ChannelSftp channel = openChannel();
        final String dest = resolvePath(name);
        try {
            if (exists(channel, dest))
                throw new ObjectAlreadyExistsException(
                        storageSystem.getStorageSystemPath(), name);

            String dir = getParentDir(dest);
            if (!exists(channel, dir))
                mkdirs(channel, dir);

            return new FilterOutputStream(channel.put(dest)) {
                @Override
                public void close() throws IOException {
                    super.close();
                    try {
                        SftpATTRS attrs = channel.stat(dest);
                        context.setFileSize(attrs.getSize());
                    } catch (SftpException e) {
                        throw new IOException("Get file size failed for path " + dest, e);
                    } finally {
                        channel.disconnect();
                    }
                }
            };
        } catch (SftpException e) {
            throw new IOException("Open output stream failed for path " + dest, e);
        }
    }

    private boolean exists(ChannelSftp channel, String path) throws SftpException {
        try {
            channel.stat(path);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE)
                return false;
            throw e;
        }
    }

    private void mkdirs(ChannelSftp channel, String dir) throws SftpException {
        String parent = getParentDir(dir);
        if (parent == null)
            return;
        if (!exists(channel, parent))
            mkdirs(channel, parent);
        channel.mkdir(dir);
    }

    private String getParentDir(String path) {
        int pos = path.lastIndexOf('/');
        if (pos == -1)
            return null;
        String dir = path.substring(0, pos);
        return dir.isEmpty() ? null : dir;
    }

    private String resolvePath(String name) {
        StringBuilder sb = new StringBuilder(storageSystem.getStorageSystemPath());
        if (sb.charAt(sb.length() - 1) != '/')
            sb.append('/');
        sb.append(name);
        return sb.toString();
    }

    @Override
    public void copyInputStream(final StorageContext context, InputStream in, String name)
            throws IOException {
        try (OutputStream out = openOutputStream(context, name)) {
            StreamUtils.copy(in, out);
        }
    }

    @Override
    public void storeFile(final StorageContext context, Path path, String name)
            throws IOException {
        try (OutputStream out = openOutputStream(context, name)) {
            Files.copy(path, out);
        }
    }

    @Override
    public void moveFile(StorageContext context, Path path, String name)
            throws IOException {
        try (OutputStream out = openOutputStream(context, name)) {
            Files.copy(path, out);
        }
        Files.delete(path);
    }

    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name)
            throws IOException {
        String src = resolvePath(name);
        final ChannelSftp channel = openChannel();
        try {
            if (!exists(channel, src))
                throw new ObjectNotFoundException(storageSystem.getStorageSystemPath(),
                        name);

            return new FilterInputStream(channel.get(src)) {
                @Override
                public void close() throws IOException {
                    super.close();
                    channel.disconnect();
                }
            };
        } catch (SftpException e) {
            throw new IOException("Open input stream failed for path " + src, e);
        }
    }

    @Override
    public Path getFile(RetrieveContext context, String name) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteObject(StorageContext context, String name) throws IOException {
        String path = resolvePath(name);
        ChannelSftp channel = openChannel();
        try {
            try {
                channel.rm(path);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE)
                    throw new ObjectNotFoundException(
                            storageSystem.getStorageSystemPath(), name);
                throw new IOException("Delete file failed for path " + path, e);
            }

            String dir = getParentDir(path);
            try {
                String basePath = storageSystem.getStorageSystemPath();
                while (!basePath.equals(dir)) {
                    @SuppressWarnings("unchecked")
                    Vector<LsEntry> v = channel.ls(dir);
                    if (v.size() > 2)
                        break;
                    channel.rmdir(dir);
                    dir = getParentDir(dir);
                }
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_FAILURE)
                    throw new IOException("Remove directory failed for path " + dir, e);
            }
        } finally {
            channel.disconnect();
        }
    }

    @Override
    public Path getBaseDirectory(StorageSystem system) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E extends Enum<E>> E queryStatus(RetrieveContext ctx, String name,
            Class<E> enumType) throws IOException {
        Map<String, String> statusFileExtensions = ctx.getStorageSystem()
                .getStatusFileExtensions();
        for (String ext : statusFileExtensions.keySet()) {
            String path = resolvePath(name) + ext;
            ChannelSftp channel = openChannel();
            try {
                if (exists(channel, path))
                    return Enum.valueOf(enumType, statusFileExtensions.get(ext));
            } catch (SftpException e) {
                throw new IOException("Exists check failed for path " + path, e);
            } finally {
                channel.disconnect();
            }
        }
        return null;
    }
}
