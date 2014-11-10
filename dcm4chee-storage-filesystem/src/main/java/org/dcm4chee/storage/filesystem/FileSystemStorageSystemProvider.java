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

package org.dcm4chee.storage.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

import org.dcm4chee.storage.KeyAlreadyExistsException;
import org.dcm4chee.storage.KeyNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.spi.StorageSystemProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@Named("filesystem")
@Dependent
public class FileSystemStorageSystemProvider implements StorageSystemProvider {

    private StorageSystem storageSystem;
    private Path basePath;

    @Override
    public void init(StorageSystem storageSystem) {
        this.storageSystem = storageSystem;
        this.basePath = Paths.get(storageSystem.getStorageSystemPath());
    }

    @Override
    public StorageSystemStatus checkStatus(long minFreeSize) {
        String mountCheckFile = storageSystem.getMountCheckFile();
        if (mountCheckFile != null && Files.exists(basePath.resolve(mountCheckFile)))
            return StorageSystemStatus.NOT_ACCESSABLE;

        long minFreeSpace0 = storageSystem.getMinFreeSpaceInBytes();
        try {
            if (minFreeSpace0 > 0 
                    && Files.getFileStore(basePath).getUsableSpace() < (minFreeSpace0 + minFreeSize))
            return StorageSystemStatus.FULL;
        } catch (IOException e) {
            //TODO
            return StorageSystemStatus.NOT_ACCESSABLE;
        }
        return StorageSystemStatus.OK;
    }

    @Override
    public OutputStream openOutputStream(StorageContext context, String name)
            throws IOException {
        Path path = basePath.resolve(name);
        Files.createDirectories(path.getParent());
        try {
            return Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            throw new KeyAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
    }

    @Override
    public void storeFile(StorageContext context, Path source, String name)
            throws IOException {
        Path target = basePath.resolve(name);
        Files.createDirectories(target.getParent());
        try {
            Files.copy(source, target);
        } catch (FileAlreadyExistsException e) {
            throw new KeyAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
    }

    @Override
    public void moveFile(StorageContext context, Path source, String name)
            throws IOException {
        Path target = basePath.resolve(name);
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target);
        } catch (FileAlreadyExistsException e) {
            throw new KeyAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
    }

    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name)
            throws IOException {
        Path path = basePath.resolve(name);
        try {
            return Files.newInputStream(path);
        } catch (FileNotFoundException e) {
            throw new KeyNotFoundException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name) throws IOException {
        Path path = basePath.resolve(name);
        if (!Files.exists(path))
            throw new KeyNotFoundException(
                    storageSystem.getStorageSystemPath(), name);
        return path;
    }

}
