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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.SortedMap;

import org.jclouds.apis.ApiMetadata;
import org.jclouds.aws.s3.AWSS3Client;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.KeyNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.MutableBlobMetadata;
import org.jclouds.io.ContentMetadata;
import org.jclouds.io.Payload;
import org.jclouds.io.PayloadSlicer;
import org.jclouds.io.internal.BasePayloadSlicer;
import org.jclouds.openstack.swift.CommonSwiftClient;
import org.jclouds.openstack.swift.SwiftApiMetadata;
import org.jclouds.openstack.swift.blobstore.functions.ResourceToObjectInfo;
import org.jclouds.openstack.swift.domain.SwiftObject;
import org.jclouds.openstack.swift.domain.internal.SwiftObjectImpl;
import org.jclouds.s3.S3ApiMetadata;
import org.jclouds.s3.domain.ObjectMetadataBuilder;

import com.google.common.collect.Maps;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@SuppressWarnings("deprecation")
public abstract class MultipartUploader {

    private PayloadSlicer slicer;
    private long partSize;
    protected BlobStoreContext context;

    private MultipartUploader(BlobStoreContext context, long partSize) {
        if (partSize <= 0L)
            throw new IllegalArgumentException();
        this.partSize = partSize;
        this.context = context;
        slicer = new BasePayloadSlicer();
    }

    public static MultipartUploader newMultipartUploader(
            BlobStoreContext context, long partSize) {
        ApiMetadata apiMetadata = context.unwrap().getProviderMetadata()
                .getApiMetadata();
        if (apiMetadata instanceof S3ApiMetadata) {
            return new AWSS3MultipartUploader(context, partSize);
        } else if (apiMetadata instanceof SwiftApiMetadata) {
            return new SwiftMultipartUploader(context, partSize);
        }
        return null;
    }

    public String upload(String container, Blob blob) throws IOException {
        MutableBlobMetadata metadata = blob.getMetadata();
        Payload payload = blob.getPayload();
        Iterable<Payload> parts = slicer.slice(payload, partSize);
        return execute(container, metadata, parts);
    }

    protected abstract String execute(String container,
            MutableBlobMetadata metadata, Iterable<Payload> slices)
            throws IOException;

    static private class AWSS3MultipartUploader extends MultipartUploader {

        private AWSS3Client client;

        private AWSS3MultipartUploader(BlobStoreContext context, long chunkSize) {
            super(context, chunkSize);
            client = context.unwrapApi(AWSS3Client.class);
        }

        @Override
        protected String execute(String container,
                MutableBlobMetadata metadata, Iterable<Payload> parts)
                throws IOException {
            ContentMetadata contentMetadata = metadata.getContentMetadata();
            String key = metadata.getName();
            ObjectMetadataBuilder builder = ObjectMetadataBuilder
                    .create()
                    .key(key)
                    .contentType(contentMetadata.getContentType())
                    .contentDisposition(contentMetadata.getContentDisposition());
            SortedMap<Integer, String> etags = Maps.newTreeMap();
            String uploadId = client.initiateMultipartUpload(container,
                    builder.build());
            try {
                int partNum = 0;
                for (Payload part : parts) {
                    partNum++;
                    String eTag;
                    try {
                        eTag = client.uploadPart(container, key, partNum,
                                uploadId, part);
                        etags.put(Integer.valueOf(partNum), eTag);
                    } catch (KeyNotFoundException e) {
                        // Try again, because of eventual consistency the upload
                        // id may not be present.
                        eTag = client.uploadPart(container, key, partNum,
                                uploadId, part);
                        etags.put(Integer.valueOf(partNum), eTag);
                    }
                }
                if (partNum == 0)
                    throw new IOException(
                            "Failed to read data from input stream");
            } catch (Exception e) {
                client.abortMultipartUpload(container, key, uploadId);
                throw e;
            }
            return client.completeMultipartUpload(container, key, uploadId,
                    etags);
        }
    }

    static private class SwiftMultipartUploader extends MultipartUploader {

        private static final String PART_SEPARATOR = "/";

        private CommonSwiftClient client;
        private ResourceToObjectInfo blob2ObjectMd;

        private SwiftMultipartUploader(BlobStoreContext context, long chunkSize) {
            super(context, chunkSize);
            client = context.unwrapApi(CommonSwiftClient.class);
            blob2ObjectMd = new ResourceToObjectInfo();
        }

        private String getPartName(String key, int partNumber) {
            return String.format("%s%s%d", key, PART_SEPARATOR, partNumber);
        }

        private SwiftObject blob2Object(Blob blob) {
            SwiftObject object = new SwiftObjectImpl((blob2ObjectMd.apply(blob
                    .getMetadata())));
            object.setPayload(checkNotNull(blob.getPayload(), "payload: "
                    + blob));
            object.setAllHeaders(blob.getAllHeaders());
            return object;
        }

        @Override
        protected String execute(String container,
                MutableBlobMetadata metadata, Iterable<Payload> parts)
                throws IOException {
            String key = metadata.getName();
            int partNum = 0;
            for (Payload part : parts) {
                partNum++;
                String partName = getPartName(key, partNum);
                Blob blobPart = context.getBlobStore().blobBuilder(partName)
                        .payload(part).contentDisposition(partName).build();
                client.putObject(container, blob2Object(blobPart));
            }
            if (partNum == 0)
                throw new IOException("Failed to read data from input stream");
            return client.putObjectManifest(container, key);
        }
    }
}
