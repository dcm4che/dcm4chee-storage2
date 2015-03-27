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

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
package org.dcm4chee.storage.encrypt;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 *
 */
public class BlockCipherInputStream extends CipherInputStream {

    public BlockCipherInputStream(InputStream in, SecretKey secretKey) {
        super(in, initCipher(secretKey, in));
    }

    private static Cipher initCipher(SecretKey secretKey, InputStream in) {
        try {
            Cipher cipher = Cipher.getInstance(secretKey.getAlgorithm()
                    + "/CBC/PKCS5Padding");
            byte[] iv = readIV(secretKey, in);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | InvalidAlgorithmParameterException | InvalidParameterSpecException
                | IOException e) {
            throw new CipherInitializationException(e);
        }
    }

    private static byte[] readIV(SecretKey secretKey, InputStream in) throws IOException,
            InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidParameterSpecException {
        byte[] iv = new byte[sizeOfIV(secretKey)];
        DataInputStream dis = new DataInputStream(in);
        dis.readFully(iv);
        return iv;
    }

    private static int sizeOfIV(SecretKey secretKey) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, InvalidParameterSpecException {
        // Determine size by generating temporary cipher with a random IV
        Cipher cipher = Cipher
                .getInstance(secretKey.getAlgorithm() + "/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.getIV().length;
    }
}
