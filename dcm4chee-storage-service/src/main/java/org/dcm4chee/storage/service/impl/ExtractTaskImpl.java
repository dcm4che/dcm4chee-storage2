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

package org.dcm4chee.storage.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import org.dcm4chee.storage.ExtractTask;
import org.dcm4chee.storage.RetrieveContext;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
class ExtractTaskImpl implements ExtractTask {

    private final ConcurrentHashMap<String, FuturePath> requestedEntries =
            new ConcurrentHashMap<String, FuturePath>();
    private RetrieveContext context;
    private String name;
    private volatile IOException ex;
    private volatile boolean finished;

    ExtractTaskImpl(RetrieveContext context, String name) {
        this.context = context;
        this.name = name;
    }

    @Override
    public void copyStream(String entryName, InputStream in) throws IOException {
        Path path = context.getFileCacheProvider()
                .toPath(context, name).resolve(entryName);
        Path tmpPath = resolveTempPath(path);
        try {
            Files.createDirectories(tmpPath.getParent());
            Files.copy(in, tmpPath);
        } catch (IOException e) {
            Files.deleteIfExists(tmpPath);
            throw e;
        }
    }

    @Override
    public void entryExtracted(String entryName) throws IOException {
        Path path = context.getFileCacheProvider()
                .toPath(context, name).resolve(entryName);
        Path tmpPath = resolveTempPath(path);
        try {
            Files.move(tmpPath, path);
        } catch (IOException e) {
            Files.deleteIfExists(tmpPath);
            Files.deleteIfExists(path);
            throw e;
        }

        context.getFileCacheProvider().register(path);

        FuturePath futurePath = requestedEntries.get(entryName);
        if (futurePath != null)
            futurePath.setPath(path);
    }

    private static Path resolveTempPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".part");
    }

    @Override
    public void finished() {
        this.finished = true;
        for (FuturePath futurePath : requestedEntries.values()) {
            synchronized (futurePath) {
                futurePath.notifyAll();
            }
        }
    }

    @Override
    public void exception(IOException ex) {
        this.ex = ex;
    }

    @Override
    public Path getFile(String entryName) throws IOException, InterruptedException {
        FuturePath newFuturePath = new FuturePath();
        FuturePath prevFuturePath = requestedEntries.putIfAbsent(entryName, newFuturePath);
        return (prevFuturePath != null ? prevFuturePath : newFuturePath).getPath();
    }

    private class FuturePath {
        private Path path;

        synchronized Path getPath() throws IOException, InterruptedException {
            while (path == null && !ExtractTaskImpl.this.finished)
                wait();
            if (path != null)
                return path;
            if (ExtractTaskImpl.this.ex != null)
                throw ExtractTaskImpl.this.ex;
            return null;
        }

        synchronized void setPath(Path path) {
            this.path = path;
            notifyAll();
        }
    }

}