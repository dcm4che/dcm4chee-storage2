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

package org.dcm4chee.storage.test.unit.archiver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.ContainerEntry;
import org.dcm4chee.storage.archiver.conf.ArchiverDeviceExtension;
import org.dcm4chee.storage.archiver.service.ArchiveEntriesStored;
import org.dcm4chee.storage.archiver.service.ArchiverContext;
import org.dcm4chee.storage.archiver.service.ArchiverService;
import org.dcm4chee.storage.archiver.service.impl.ArchiverServiceImpl;
import org.dcm4chee.storage.conf.Container;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.service.impl.RetrieveServiceImpl;
import org.dcm4chee.storage.service.impl.StorageServiceImpl;
import org.dcm4chee.storage.test.unit.util.TransientDirectory;
import org.dcm4chee.storage.zip.ZipContainerProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@RunWith(Arquillian.class)
public class ArchiverServiceTest {

    private static final String DIGEST = "1043bfc77febe75fafec0c4309faccf1";
    private static final String DIR_PATH = "target/test-storage/archiver";
    private static final String NAME = "test.zip";
    private static final String ENTRY_FILE = "entry";
    private static final String[] ENTRY_NAMES = { "entry-1", "entry-2",
            "entry-3" };
    private static final byte[] ENTRY = { 'e', 'n', 't', 'r', 'y' };

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClass(ArchiverServiceImpl.class)
                .addClass(StorageServiceImpl.class)
                .addClass(RetrieveServiceImpl.class)
                .addClass(FileSystemStorageSystemProvider.class)
                .addClass(ZipContainerProvider.class)
                .addClass(ContextObserver.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private ArchiverService service;

    @Inject
    private ContextObserver observer;

    @Produces
    private static Device device = new Device("test");

    @Rule
    public TransientDirectory dir = new TransientDirectory(DIR_PATH);

    private StorageDeviceExtension storageExt;
    private ArchiverDeviceExtension archiverExt;
    private StorageSystemGroup group;
    private StorageSystem system;
    private Container container;

    @Before
    public void setup() throws IOException {
        archiverExt = new ArchiverDeviceExtension();
        archiverExt.setMaxRetries(0);
        device.addDeviceExtension(archiverExt);
        storageExt = new StorageDeviceExtension();
        device.addDeviceExtension(storageExt);
        group = new StorageSystemGroup();
        group.setGroupID("nearline");
        storageExt.addStorageSystemGroup(group);
        system = new StorageSystem();
        system.setProviderName("org.dcm4chee.storage.filesystem");
        system.setStorageSystemID("hsm1");
        system.setStorageSystemPath(DIR_PATH);
        system.setStorageSystemStatus(StorageSystemStatus.OK);
        group.addStorageSystem(system);
        group.activate(system, true);
        container = new Container();
        container.setChecksumEntry("MD5SUM");
        container.setProviderName("org.dcm4chee.storage.zip");
        group.setContainer(container);
        observer.reset();
    }

    @After
    public void teardown() {
        device.removeDeviceExtension(storageExt);
        storageExt = null;
        device.removeDeviceExtension(archiverExt);
        archiverExt = null;
    }

    @Test
    public void testStore() throws Exception {
        ArchiverContext ctx = service.createContext(group.getGroupID(), NAME,
                "MD5");
        Path entryPath = createFile(ENTRY, ENTRY_FILE);
        for (String name : ENTRY_NAMES) {
            ctx.putEntry(new ContainerEntry(name, entryPath, DIGEST), name);
        }
        service.store(ctx, 0);
        Assert.assertNotNull(observer.getContext());
        Assert.assertEquals(system.getStorageSystemID(), observer.getContext()
                .getStorageSystemID());
    }

    private Path createFile(byte[] b, String name) throws IOException {
        Path path = dir.getPath().resolve(name);
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(b);
        }
        return path;
    }

    @RequestScoped
    static class ContextObserver {

        private ArchiverContext context;

        public void observe(
                @Observes @ArchiveEntriesStored ArchiverContext context) {
            this.context = context;
        }

        void reset() {
            context = null;
        }

        ArchiverContext getContext() {
            return context;
        }
    }
};
