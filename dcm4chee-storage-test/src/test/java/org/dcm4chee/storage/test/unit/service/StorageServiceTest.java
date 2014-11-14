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

package org.dcm4chee.storage.test.unit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.conf.StorageSystemGroup;
import org.dcm4chee.storage.conf.StorageSystemStatus;
import org.dcm4chee.storage.filesystem.FileSystemStorageSystemProvider;
import org.dcm4chee.storage.service.StorageService;
import org.dcm4chee.storage.service.impl.StorageServiceImpl;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@RunWith(Arquillian.class)
public class StorageServiceTest {

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(StorageServiceImpl.class)
            .addClass(FileSystemStorageSystemProvider.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    StorageService service;

    @Produces
    static Device device = new Device("test");

    StorageDeviceExtension ext;
    StorageSystemGroup fsGroup;
    StorageSystem fs1;
    StorageSystem fs2;
    StorageSystem fs3;

    @Before
    public void setup() throws IOException {
        ext = new StorageDeviceExtension();
        device.addDeviceExtension(ext);
        fsGroup = new StorageSystemGroup();
        fsGroup.setGroupID("fs");
        ext.addStorageSystemGroup(fsGroup);
        fsGroup.addStorageSystem(fs1 = createStorageSystem("fs1", "fs2"));
        fsGroup.addStorageSystem(fs2 = createStorageSystem("fs2", "fs3"));
        fsGroup.addStorageSystem(fs3 = createStorageSystem("fs3", null));
    }

    @After
    public void teardown() {
        device.removeDeviceExtension(ext);
        ext = null;
        fsGroup = null;
        fs1 = null;
        fs2 = null;
        fs3 = null;
    }

    private StorageSystem createStorageSystem(String id, String next)
            throws IOException {
        StorageSystem system = new StorageSystem();
        system.setProviderName("org.dcm4chee.storage.filesystem");
        system.setStorageSystemID(id);
        system.setNextStorageSystemID(next);
        system.setStorageSystemPath("target/test-storage/" + id);
        system.setStorageSystemStatus(StorageSystemStatus.OK);
        system.setMountCheckFile("NO_MOUNT");
        Path dir = Files.createDirectories(
                Paths.get(system.getStorageSystemPath()));
        Files.deleteIfExists(dir.resolve(system.getMountCheckFile()));
        return system;
    }

    @Test
    public void testSelectStorageSystem() throws Exception {
        fsGroup.activate(fs1, true);
        fsGroup.activate(fs2, true);
        Assert.assertArrayEquals(
                new String[]{"fs1", "fs2"},
                fsGroup.getActiveStorageSystemIDs());
        Assert.assertEquals("fs3", fsGroup.getNextStorageSystemID());

        Assert.assertSame(fs1, service.selectStorageSystem("fs", 0));
        Assert.assertSame(fs2, service.selectStorageSystem("fs", 0));
        Assert.assertSame(fs1, service.selectStorageSystem("fs", 0));
        Assert.assertFalse(ext.isDirty());

        createMountCheckFile(fs2);
        Assert.assertSame(fs3, service.selectStorageSystem("fs", 0));

        Assert.assertEquals(StorageSystemStatus.NOT_ACCESSABLE,
                fs2.getStorageSystemStatus());
        Assert.assertArrayEquals(
                new String[]{"fs1", "fs3"},
                fsGroup.getActiveStorageSystemIDs());
        Assert.assertNull(fsGroup.getNextStorageSystemID());
        Assert.assertTrue(ext.isDirty());
    }


    private void createMountCheckFile(StorageSystem system) throws IOException {
        Path mountCheckFile = Paths.get(
                system.getStorageSystemPath(),
                system.getMountCheckFile());
        Files.createFile(mountCheckFile);
    }
}
