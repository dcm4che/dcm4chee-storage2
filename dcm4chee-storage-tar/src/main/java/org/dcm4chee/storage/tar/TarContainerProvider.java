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
 * Portions created by the Initial Developer are Copyright (C) 2011-2014
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

package org.dcm4chee.storage.tar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.dcm4chee.storage.ChecksumException;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.ExtractTask;
import org.dcm4chee.storage.ObjectNotFoundException;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.Container;
import org.dcm4chee.storage.spi.ContainerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Steve Kroetsch <steve.kroetsch@hotmail.com>
 *
 */
@Named("org.dcm4chee.storage.tar")
@Dependent
public class TarContainerProvider implements ContainerProvider {

    private static final Logger LOG = LoggerFactory
            .getLogger(TarContainerProvider.class);

    private Container container;

    @Override
    public void init(Container container) {
        this.container = container;
    }

    @Override
    public void writeEntriesTo(StorageContext context,
            List<ContainerEntry> entries, OutputStream out) throws IOException {
        TarArchiveOutputStream tar = new TarArchiveOutputStream(out);
        String checksumEntry = container.getChecksumEntry();
        if (checksumEntry != null) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ContainerEntry.writeChecksumsTo(entries, bout);
            TarArchiveEntry tarEntry = new TarArchiveEntry(checksumEntry);
            tarEntry.setSize(bout.size());
            tar.putArchiveEntry(tarEntry);
            tar.write(bout.toByteArray());
            tar.closeArchiveEntry();
        }
        for (ContainerEntry entry : entries) {
            Path path = entry.getPath();
            TarArchiveEntry tarEntry = new TarArchiveEntry(entry.getName());
            tarEntry.setModTime(Files.getLastModifiedTime(path).toMillis());
            tarEntry.setSize(Files.size(path));
            tar.putArchiveEntry(tarEntry);
            Files.copy(path, tar);
            tar.closeArchiveEntry();
        }
        tar.finish();
    }

    @Override
    public InputStream seekEntry(RetrieveContext ctx, String name,
            String entryName, InputStream in) throws IOException {
        TarArchiveInputStream tar = new TarArchiveInputStream(in);
        String checksumEntry = container.getChecksumEntry();
        TarArchiveEntry nextEntry;
        while ((nextEntry = tar.getNextTarEntry()) != null) {
            String nextEntryName = nextEntry.getName();
            if (nextEntry.isDirectory() || nextEntryName.equals(checksumEntry))
                continue;

            if (nextEntryName.equals(entryName))
                return tar;
        }
        throw new ObjectNotFoundException(ctx.getStorageSystem()
                .getStorageSystemPath(), name, entryName);
    }

    @Override
    public void extractEntries(RetrieveContext ctx, String name,
            ExtractTask extractTask, InputStream in) throws IOException {
        TarArchiveInputStream tar = new TarArchiveInputStream(in);
        TarArchiveEntry entry = skipDirectoryEntries(tar);
        if (entry == null)
            throw new IOException("No entries in " + name);
        String entryName = entry.getName();
        Map<String, byte[]> checksums = null;
        String checksumEntry = container.getChecksumEntry();
        MessageDigest digest = null;
        if (checksumEntry != null) {
            if (checksumEntry.equals(entryName)) {
                try {
                    digest = MessageDigest.getInstance(ctx.getStorageSystem().getStorageSystemGroup()
                            .getDigestAlgorithm());
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                checksums = ContainerEntry.readChecksumsFrom(tar);
            } else
                LOG.warn("Misssing checksum entry in %s", name);
            entry = skipDirectoryEntries(tar);
        }

        for (; entry != null; entry = skipDirectoryEntries(tar)) {
            entryName = entry.getName();
            InputStream in0 = tar;
            byte[] checksum = null;
            if (checksums != null && digest != null) {
                checksum = checksums.remove(entryName);
                if (checksum == null)
                    throw new ChecksumException(
                            "Missing checksum for container entry: "
                                    + entryName + " in " + name);
                digest.reset();
                in0 = new DigestInputStream(tar, digest);
            }

            extractTask.copyStream(entryName, in0);

            if (checksums != null && digest != null) {
                if (!Arrays.equals(digest.digest(), checksum)) {
                    throw new ChecksumException(
                            "Checksums do not match for container entry: "
                                    + entry.getName() + " in " + name);
                }
            }

            extractTask.entryExtracted(entryName);
        }
    }

    private TarArchiveEntry skipDirectoryEntries(TarArchiveInputStream tar)
            throws IOException {
        for (TarArchiveEntry entry = tar.getNextTarEntry(); entry != null; entry = tar
                .getNextTarEntry()) {
            if (!entry.isDirectory())
                return entry;
        }
        return null;
    }
}
