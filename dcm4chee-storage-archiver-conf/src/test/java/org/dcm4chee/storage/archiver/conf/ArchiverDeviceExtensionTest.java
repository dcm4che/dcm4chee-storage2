/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contentsOfthis file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copyOfthe License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is partOfdcm4che, an implementationOfDICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial DeveloperOfthe Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contentsOfthis file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisionsOfthe GPL or the LGPL are applicable instead
 * of those above. If you wish to allow useOfyour versionOfthis file only
 * under the termsOfeither the GPL or the LGPL, and not to allow others to
 * use your versionOfthis file under the termsOfthe MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your versionOfthis file under
 * the termsOfany oneOfthe MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.storage.archiver.conf;

import java.io.InputStream;
import java.util.Properties;

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4che3.net.Device;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ArchiverDeviceExtensionTest {

    private static final String DEVICE_NAME = "dcm4chee-archiver-test";

    DicomConfiguration config;

    @Before
    public void setUp() throws Exception {

        DicomConfigurationBuilder builder;
        if (System.getProperty("ldap") != null) {
            Properties env = new Properties();
            try (InputStream inStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("ldap.properties")) {
                env.load(inStream);
            }
            builder = DicomConfigurationBuilder
                    .newLdapConfigurationBuilder(env);
        } else {
            builder = DicomConfigurationBuilder
                    .newJsonConfigurationBuilder("target/config.json");
        }
        builder.registerDeviceExtension(ArchiverDeviceExtension.class);
        config = builder.cache().build();
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        config.close();
    }

    @Test
    public void test() throws Exception {
        Device dev1 = new Device(DEVICE_NAME);
        ArchiverDeviceExtension ext1 = createArchiverDeviceExtension();
        dev1.addDeviceExtension(ext1);
        config.persist(dev1);
        Device dev2 = config.findDevice(DEVICE_NAME);
        ArchiverDeviceExtension ext2 = dev2
                .getDeviceExtension(ArchiverDeviceExtension.class);
        assertEquals(ext1, ext2);
    }

    private void cleanUp() throws Exception {
        try {
            config.removeDevice(DEVICE_NAME);
        } catch (ConfigurationNotFoundException e) {
        }
    }

    private ArchiverDeviceExtension createArchiverDeviceExtension() {
        ArchiverDeviceExtension ext = new ArchiverDeviceExtension();
        ext.setMaxRetries(48);
        ext.setRetryInterval(7200);
        ext.setVerifyArchive(false);
        return ext;
    }

    private void assertEquals(ArchiverDeviceExtension expected,
            ArchiverDeviceExtension actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.getMaxRetries(), actual.getMaxRetries());
        Assert.assertEquals(expected.getRetryInterval(),
                actual.getRetryInterval());
        Assert.assertEquals(expected.isVerifyArchive(),
                actual.isVerifyArchive());
    }
}
