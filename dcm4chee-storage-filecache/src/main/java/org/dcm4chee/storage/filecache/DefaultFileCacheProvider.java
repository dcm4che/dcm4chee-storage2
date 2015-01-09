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

package org.dcm4chee.storage.filecache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.conf.FileCache;
import org.dcm4chee.storage.spi.FileCacheProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Named("org.dcm4chee.storage.filecache")
@Dependent
public class DefaultFileCacheProvider implements FileCacheProvider {

    private static final Logger LOG = LoggerFactory
            .getLogger(DefaultFileCacheProvider.class);

    private FileCache cache;
    private org.dcm4che3.filecache.FileCache impl;

    @Inject
    private Device device;

    @Override
    public void init(FileCache fileCache) {
        this.cache = fileCache;
        this.impl = new org.dcm4che3.filecache.FileCache();
        impl.setFileCacheRootDirectory(
                Paths.get(fileCache.getFileCacheRootDirectory()));
        impl.setJournalRootDirectory(
                Paths.get(fileCache.getJournalRootDirectory()));
        impl.setJournalFileName(fileCache.getJournalFileName());
        impl.setJournalDirectoryName(fileCache.getJournalDirectoryName());
        impl.setJournalFileNamePattern(fileCache.getJournalFileNamePattern());
        impl.setOrphanedFileName(fileCache.getOrphanedFileName());
        impl.setJournalMaxEntries(fileCache.getJournalMaxEntries());
        impl.setLeastRecentlyUsed(
                fileCache.getCacheAlgorithm() == FileCache.Algorithm.LRU);
    }

    @Override
    public Path toPath(RetrieveContext ctx, String name) {
        return impl.getFileCacheRootDirectory().resolve(
                Paths.get(
                    ctx.getStorageSystem().getStorageSystemID(),
                    name));
    }

    @Override
    public Path toPath(RetrieveContext ctx, String name, String entryName) {
        return impl.getFileCacheRootDirectory().resolve(
                Paths.get(
                    ctx.getStorageSystem().getStorageSystemID(),
                    name,
                    entryName));
    }

    @Override
    public void register(Path path) throws IOException {
        impl.register(path);
        if (cache.getMinFreeSpace() != null) {
            final long size = cache.getMinFreeSpaceInBytes()
                    - Files.getFileStore(path).getUsableSpace();
            if (size > 0)
                device.execute(new Runnable(){

                    @Override
                    public void run() {
                        try {
                            impl.free(size);
                        } catch (IOException e) {
                            LOG.warn("Failed to free space from {}", impl, e);
                        }
                    }});
        }
    }

    @Override
    public boolean access(Path path) throws IOException {
        return impl.access(path);
    }

}
