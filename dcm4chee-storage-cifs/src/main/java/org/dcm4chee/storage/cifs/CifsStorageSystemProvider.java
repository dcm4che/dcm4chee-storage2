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

package org.dcm4chee.storage.cifs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

import jcifs.Config;
import jcifs.smb.NtStatus;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.spi.StorageSystemProvider;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */

@Named("org.dcm4chee.storage.cifs")
@Dependent
public class CifsStorageSystemProvider implements StorageSystemProvider {

    static {
        Config.setProperty("jcifs.smb.client.attrExpirationPeriod", String.valueOf(0));
    }

    private SmbFile baseDir;
    private StorageSystem storageSystem;

    @Override
    public void init(StorageSystem storageSystem) {
        this.storageSystem = storageSystem;
        String url = constructUrl(storageSystem);
        try {
            baseDir = new SmbFile(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL constructed is invalid: " + url, e);
        }
    }

    private String constructUrl(StorageSystem storageSystem) {
        // Syntax: smb://[[[domain;]username[:password]@]server[:port]/[[share/[dir/]file]]]
        StringBuilder sb = new StringBuilder("smb://");
        String uname = storageSystem.getStorageSystemIdentity();
        if (uname != null) {
            String domain = storageSystem.getStorageSystemDomain();
            if (domain != null) {
                sb.append(domain);
                sb.append(';');
            }
            sb.append(uname);
            String pwd = storageSystem.getStorageSystemCredential();
            if (pwd != null) {
                sb.append(':');
                sb.append(pwd);
            }
            sb.append("@");
        }
        sb.append(storageSystem.getStorageSystemHostname());
        int port = storageSystem.getStorageSystemPort();
        if (port != -1) {
            sb.append(':');
            sb.append(port);
        }
        sb.append(storageSystem.getStorageSystemPath());
        if (sb.charAt(sb.length() - 1) != '/')
            sb.append('/');
        return sb.toString();
    }

    @Override
    public void checkWriteable() throws IOException {
    }

    @Override
    public long getUsableSpace() throws IOException {
        return baseDir.getDiskFreeSpace();
    }

    @Override
    public OutputStream openOutputStream(final StorageContext context, String name)
            throws IOException {
        final SmbFile target = new SmbFile(baseDir, name);
        if (target.exists())
            throw new ObjectAlreadyExistsException(storageSystem.getStorageSystemPath(),
                    name);
        SmbFile dir = new SmbFile(target.getParent());
        if (!dir.exists())
            dir.mkdirs();
        return new SmbFileOutputStream(target) {
            @Override
            public void close() throws IOException {
                super.close();
                context.setFileSize(target.getContentLength());
            }
        };
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
        SmbFile file = new SmbFile(baseDir, name);
        try {
            return file.getInputStream();
        } catch (SmbException e) {
            throw isNotFound(e.getNtStatus()) ? new ObjectNotFoundException(
                    storageSystem.getStorageSystemPath(), name) : e;
        }
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteObject(StorageContext ctx, String name) throws IOException {
        SmbFile file = new SmbFile(baseDir, name);
        try {
            file.delete();
        } catch (SmbException e) {
            throw isNotFound(e.getNtStatus()) ? new ObjectNotFoundException(
                    storageSystem.getStorageSystemPath(), name) : e;
        }

        try {
            SmbFile dir = new SmbFile(file.getParent());
            while (!baseDir.equals(dir)) {
                if (dir.list().length > 0)
                    break;
                dir.delete();
                dir = new SmbFile(dir.getParent());
            }
        } catch (SmbException e) {
            if (e.getNtStatus() != NtStatus.NT_STATUS_CANNOT_DELETE)
                throw e;
        }
    }

    private boolean isNotFound(int ntStatus) {
        switch (ntStatus) {
        case NtStatus.NT_STATUS_NO_SUCH_FILE:
        case NtStatus.NT_STATUS_OBJECT_NAME_INVALID:
        case NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND:
        case NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND:
            return true;
        default:
            return false;
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
            SmbFile file = new SmbFile(baseDir, name + ext);
            if (file.exists())
                return Enum.valueOf(enumType, statusFileExtensions.get(ext));
        }
        return null;
    }
}
