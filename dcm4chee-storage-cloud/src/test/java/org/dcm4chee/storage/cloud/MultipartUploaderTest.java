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

package org.dcm4chee.storage.cloud;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

import org.dcm4chee.storage.cloud.MultipartUploader;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
public class MultipartUploaderTest {

    private static final long DEFAULT_FILE_SIZE = (20 * 1024 * 1024) + 99;
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    private static final Path DIR = Paths.get("target/test-storage/cloud");
    private static final Path FILE = DIR.resolve("multipart.test");
    private static final String AWSS3_API = "aws-s3";
    private static final String SWIFT_API = "swift";

    private static final String UPLOAD_FILESIZE_PROPERTY = "upload.filesize";
    private static final String UPLOAD_CHUNKSIZE_PROPERTY = "upload.chunksize";

    private static final String AWSS3_ENDPOINT_PROPERTY = "awss3.endpoint";
    private static final String AWSS3_IDENTITY_PROPERTY = "awss3.identity";
    private static final String AWSS3_CREDENTIAL_PROPERTY = "awss3.credential";
    private static final String AWSS3_CONTAINER_PROPERTY = "awss3.container";

    private static final String SWIFT_ENDPOINT_PROPERTY = "swift.endpoint";
    private static final String SWIFT_IDENTITY_PROPERTY = "swift.identity";
    private static final String SWIFT_CREDENTIAL_PROPERTY = "swift.credential";
    private static final String SWIFT_CONTAINER_PROPERTY = "swift.container";

    private static final Properties PROPS = new Properties();

    @BeforeClass
    public static void beforeClass() throws IOException {
        loadProperties();
        createUploadFile();
    }

    private static void loadProperties() throws IOException {
        String filePath = System.getProperty(MultipartUploaderTest.class
                .getName());
        if (filePath != null) {
            FileInputStream in = new FileInputStream(filePath);
            try {
                PROPS.load(in);
            } finally {
                in.close();
            }
        }
    }

    private static void createUploadFile() throws IOException {
        if (!Files.exists(FILE)) {
            Files.createDirectories(FILE.getParent());
            OutputStream out = null;
            long fileSize = getUploadFileSize();
            try {
                try {
                    out = new BufferedOutputStream(new FileOutputStream(
                            FILE.toFile()));
                    for (int i = 0; i < fileSize; i++)
                        out.write((byte) 'x');
                } finally {
                    if (out != null)
                        out.close();
                }
            } catch (IOException e) {
                if (Files.exists(FILE))
                    Files.delete(FILE);
                throw e;
            }
        }
    }

    private static long getUploadFileSize() {
        String val = PROPS.getProperty(UPLOAD_FILESIZE_PROPERTY);
        return val == null ? DEFAULT_FILE_SIZE : Long.valueOf(val);
    }

    private static long getUploadChunkSize() {
        String val = PROPS.getProperty(UPLOAD_CHUNKSIZE_PROPERTY);
        return val == null ? DEFAULT_CHUNK_SIZE : Long.valueOf(val);
    }

    @Test
    public void testUploadWithAWSS2() throws IOException {
        uploadAndAssertObjectExists(AWSS3_API,
                PROPS.getProperty(AWSS3_ENDPOINT_PROPERTY),
                PROPS.getProperty(AWSS3_CONTAINER_PROPERTY),
                PROPS.getProperty(AWSS3_IDENTITY_PROPERTY),
                PROPS.getProperty(AWSS3_CREDENTIAL_PROPERTY));
    }

    @Test
    public void testUploadWithSwift() throws IOException {
        uploadAndAssertObjectExists(SWIFT_API,
                PROPS.getProperty(SWIFT_ENDPOINT_PROPERTY),
                PROPS.getProperty(SWIFT_CONTAINER_PROPERTY),
                PROPS.getProperty(SWIFT_IDENTITY_PROPERTY),
                PROPS.getProperty(SWIFT_CREDENTIAL_PROPERTY));
    }

    private void uploadAndAssertObjectExists(String api, String endpoint,
            String container, String identity, String credential)
            throws IOException {

        Assume.assumeNotNull(endpoint, container, identity, credential);

        BlobStoreContext context = ContextBuilder.newBuilder(api)
                .endpoint(endpoint).credentials(identity, credential)
                .buildView(BlobStoreContext.class);
        String name = UUID.randomUUID().toString();
        BlobStore blobStore = context.getBlobStore();
        try {
            @SuppressWarnings("deprecation")
            Blob blob = blobStore.blobBuilder(name).payload(FILE.toFile())
                    .build();
            MultipartUploader uploader = MultipartUploader
                    .newMultipartUploader(context, getUploadChunkSize());
            uploader.upload(container, blob);

            Assert.assertTrue(blobStore.blobExists(container, name));
            // long len = blobStore.blobMetadata(container, name)
            // .getContentMetadata().getContentLength().longValue();
            // Assert.assertEquals(FILE_SIZE, len);
        } finally {
            context.close();
            if (blobStore.blobExists(container, name))
                blobStore.removeBlob(container, name);
        }
    }
}
