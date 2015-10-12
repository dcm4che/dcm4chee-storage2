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
package org.dcm4chee.storage.service.decorators;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;

import org.dcm4chee.conf.decorators.DynamicDecoratorWrapper;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.service.StorageService;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@Decorator
public abstract class StorageServiceDynamicDecorator extends
        DynamicDecoratorWrapper<StorageService> implements StorageService {

    @Inject
    @Delegate
    StorageService delegate;

    @Override
    public StorageSystem selectStorageSystem(String storageSystemGroupID, long size) {
        return wrapWithDynamicDecorators(delegate).selectStorageSystem(storageSystemGroupID, size);
    }

    @Override
    public StorageSystemGroup selectBestStorageSystemGroup(String groupType) {
        return wrapWithDynamicDecorators(delegate).selectBestStorageSystemGroup(groupType);
    }

    @Override
    public Path getBaseDirectory(StorageSystem storageSystem) {
        return wrapWithDynamicDecorators(delegate).getBaseDirectory(storageSystem);
    }

    @Override
    public StorageContext createStorageContext(StorageSystem storageSystem) {
        return wrapWithDynamicDecorators(delegate).createStorageContext(storageSystem);
    }

    @Override
    public OutputStream openOutputStream(StorageContext context, String name) throws IOException {
        return wrapWithDynamicDecorators(delegate).openOutputStream(context, name);
    }

    @Override
    public void copyInputStream(StorageContext context, InputStream in, String name)
            throws IOException {
        wrapWithDynamicDecorators(delegate).copyInputStream(context, in, name);
    }

    @Override
    public void storeContainerEntries(StorageContext context, List<ContainerEntry> entries,
            String name) throws IOException {
        wrapWithDynamicDecorators(delegate).storeContainerEntries(context, entries, name);
    }

    @Override
    public void storeFile(StorageContext context, Path path, String name) throws IOException {
        wrapWithDynamicDecorators(delegate).storeFile(context, path, name);
    }

    @Override
    public void moveFile(StorageContext context, Path path, String name) throws IOException {
        wrapWithDynamicDecorators(delegate).moveFile(context, path, name);
    }

    @Override
    public void deleteObject(StorageContext context, String name) throws IOException {
        wrapWithDynamicDecorators(delegate).deleteObject(context, name);
    }

    @Override
    public void syncFiles(StorageSystem storageSystem, List<String> names) throws IOException {
        wrapWithDynamicDecorators(delegate).syncFiles(storageSystem, names);
    }
}