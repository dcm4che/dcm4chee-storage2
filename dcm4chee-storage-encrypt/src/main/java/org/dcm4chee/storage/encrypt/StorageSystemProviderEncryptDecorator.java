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
 * Portions created by the Initial Developer are Copyright (C) 2011-2015
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

package org.dcm4chee.storage.encrypt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.SecretKey;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;

import org.dcm4che3.net.Device;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.storage.RetrieveContext;
import org.dcm4chee.storage.StorageContext;
import org.dcm4chee.storage.conf.StorageDevice;
import org.dcm4chee.storage.conf.StorageSystem;
import org.dcm4chee.storage.spi.StorageSystemProvider;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
@Decorator
public abstract class StorageSystemProviderEncryptDecorator implements
        StorageSystemProvider {

    @Inject
    @Delegate
    StorageSystemProvider storageSystemProvider;

    @Inject @StorageDevice
    private Device device;

    private SecretKey secretKey;

    public void init(StorageSystem storageSystem) {
        String keyAlias = storageSystem.getEncryptionKeyAlias();
        if (keyAlias != null)
            initSecretKey(keyAlias);
        storageSystemProvider.init(storageSystem);
    }

    private void initSecretKey(String keyAlias) {
        try {
            KeyStore ks = loadKeyStore();
            SecretKey key = (SecretKey) ks.getKey(keyAlias, device.getKeyStoreKeyPin()
                    .toCharArray());
            if (key == null)
                throw new IllegalStateException("Secret key alias " + keyAlias
                        + " not found in keystore "
                        + StringUtils.replaceSystemProperties(device.getKeyStoreURL()));
            secretKey = key;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException
                | IOException | UnrecoverableKeyException e) {
            throw new IllegalStateException("Failed to initialize secret key for alias "
                    + keyAlias, e);
        }
    }

    private KeyStore loadKeyStore() throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException {
        KeyStore ks = KeyStore.getInstance(device.getKeyStoreType());
        try (InputStream in = StreamUtils.openFileOrURL(StringUtils
                .replaceSystemProperties(device.getKeyStoreURL()))) {
            ks.load(in, device.getKeyStorePin().toCharArray());
        }
        return ks;
    }

    @SuppressWarnings("resource")
    @Override
    public OutputStream openOutputStream(StorageContext context, String name)
            throws IOException {
        OutputStream out = storageSystemProvider.openOutputStream(context, name);
        return secretKey != null ? new BlockCipherOutputStream(out, secretKey) : out;
    }

    @Override
    public void copyInputStream(StorageContext context, InputStream in, String name)
            throws IOException {
        if (secretKey != null) {
            try (OutputStream out = openOutputStream(context, name)) {
                StreamUtils.copy(in, out);
            }
        } else
            storageSystemProvider.copyInputStream(context, in, name);
    }

    @Override
    public void storeFile(StorageContext context, Path path, String name)
            throws IOException {
        if (secretKey != null) {
            try (OutputStream out = openOutputStream(context, name)) {
                Files.copy(path, out);
            }
        } else
            storageSystemProvider.storeFile(context, path, name);
    }

    @Override
    public void moveFile(StorageContext context, Path path, String name)
            throws IOException {
        if (secretKey != null) {
            try (OutputStream out = openOutputStream(context, name)) {
                Files.copy(path, out);
            }
            Files.delete(path);
        } else
            storageSystemProvider.moveFile(context, path, name);
    }

    @SuppressWarnings("resource")
    @Override
    public InputStream openInputStream(RetrieveContext ctx, String name)
            throws IOException {
        InputStream in = storageSystemProvider.openInputStream(ctx, name);
        return secretKey != null ? new BlockCipherInputStream(in, secretKey) : in;
    }

    @Override
    public Path getFile(RetrieveContext ctx, String name) throws IOException {
        if (secretKey != null)
            throw new UnsupportedOperationException();
        else
            return storageSystemProvider.getFile(ctx, name);
    }
}
