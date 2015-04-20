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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

import org.dcm4chee.storage.ObjectAlreadyExistsException;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.spi.StorageSystemProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@Named("org.dcm4chee.storage.filesystem")
@Dependent
public class FileSystemStorageSystemProvider implements StorageSystemProvider {

    private StorageSystem storageSystem;
    private Path basePath;

    @Override
    public Path getBaseDirectory(StorageSystem system) {
        return basePath;
    }

    @Override
    public void init(StorageSystem storageSystem) {
        this.storageSystem = storageSystem;
        this.basePath = Paths.get(storageSystem.getStorageSystemPath());
    }

    @Override
    public void checkWriteable() throws IOException {
        String path = storageSystem.getMountCheckFile();
        if (path != null && Files.exists(basePath.resolve(path)))
            throw new IOException("Mount check file: "
                    + basePath.resolve(path) + " appears");
    }

    @Override
    public long getUsableSpace() throws IOException {
        return Files.getFileStore(basePath).getUsableSpace();
    }

    @Override
    public OutputStream openOutputStream(final StorageContext context, String name)
            throws IOException {
        final Path path = basePath.resolve(name);
        Files.createDirectories(path.getParent());
        try {
            return new FilterOutputStream(
                    Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {

                @Override
                public void close() throws IOException {
                    super.close();
                    context.setFileSize(Files.size(path));
                }};
        } catch (FileAlreadyExistsException e) {
            throw new ObjectAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
    }

    @Override
    public void copyInputStream(StorageContext context, InputStream source,
            String name) throws IOException {
        Path target = basePath.resolve(name);
        Files.createDirectories(target.getParent());
        try {
            Files.copy(source, target);
        } catch (FileAlreadyExistsException e) {
            throw new ObjectAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
        context.setFileSize(Files.size(target));
    }

    @Override
    public void storeFile(StorageContext context, Path source, String name)
            throws IOException {
        Path target = basePath.resolve(name);
        Files.createDirectories(target.getParent());
        try {
            Files.copy(source, target);
        } catch (FileAlreadyExistsException e) {
            throw new ObjectAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
        context.setFileSize(Files.size(target));
    }

    @Override
    public void moveFile(StorageContext context, Path source, String name)
            throws IOException {
        Path target = basePath.resolve(name);
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target);
        } catch (FileAlreadyExistsException e) {
            throw new ObjectAlreadyExistsException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
        context.setFileSize(Files.size(target));
    }

    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name)
            throws IOException {
        Path path = basePath.resolve(name);
        try {
            return Files.newInputStream(path);
        } catch (NoSuchFileException e) {
            throw new ObjectNotFoundException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name) throws IOException {
        Path path = basePath.resolve(name);
        if (!Files.exists(path))
            throw new ObjectNotFoundException(
                    storageSystem.getStorageSystemPath(), name);
        return path;
    }

    @Override
    public void deleteObject(StorageContext ctx, String name) throws IOException {
        Path path = basePath.resolve(name);
        try {
            Files.delete(path);
        } catch (NoSuchFileException e) {
            throw new ObjectNotFoundException(
                    storageSystem.getStorageSystemPath(), name, e);
        }
        try {
            Path dir = path.getParent();
            while (!basePath.equals(dir)) {
                Files.delete(dir);
                dir = dir.getParent();
            }
        } catch (DirectoryNotEmptyException e) {}
    }

    @Override
    public <E extends Enum<E>> E queryStatus(RetrieveContext ctx, String name,
            Class<E> enumType) throws IOException {
        Map<String, String> statusFileExtensions = ctx.getStorageSystem()
                .getStatusFileExtensions();
        for (String ext : statusFileExtensions.keySet()) {
            Path path = basePath.resolve(name + ext);
            if (Files.exists(path))
                return Enum.valueOf(enumType, statusFileExtensions.get(ext));
        }
        return null;
    }
}
