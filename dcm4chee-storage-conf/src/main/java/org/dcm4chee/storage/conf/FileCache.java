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

package org.dcm4chee.storage.conf;

import java.io.Serializable;

import javax.enterprise.inject.Instance;

import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;
import org.dcm4che3.conf.core.api.LDAP;
import org.dcm4chee.storage.spi.FileCacheProvider;

/**
 * @author Gunter Zeilinger<gunterze@gmail.com>
 *
 */
@LDAP(objectClasses = "dcmStorageFileCache")
@ConfigurableClass
public class FileCache implements Serializable{

    private static final long serialVersionUID = 5834028413375203606L;

    public enum Algorithm {
        FIFO,
        LRU
    }

    @ConfigurableProperty(name = "dcmProviderName")
    private String providerName;

    @ConfigurableProperty(name = "dcmStorageFileCacheRootDirectory")
    private String fileCacheRootDirectory;

    @ConfigurableProperty(name = "dcmStorageFileCacheJournalRootDirectory")
    private String journalRootDirectory;

    @ConfigurableProperty(name = "dcmStorageFileCacheJournalFileName", defaultValue = "journal")
    private String journalFileName = "journal";

    @ConfigurableProperty(name = "dcmStorageFileCacheOrphanedFileName", defaultValue = "orphaned")
    private String orphanedFileName = "orphaned";

    @ConfigurableProperty(name = "dcmStorageFileCacheJournalDirectoryName", defaultValue = "journal.d")
    private String journalDirectoryName = "journal.d";

    @ConfigurableProperty(name = "dcmStorageFileCacheJournalFileNamePattern", defaultValue = "yyyyMMdd/HHmmss.SSS")
    private String journalFileNamePattern = "yyyyMMdd/HHmmss.SSS";

    @ConfigurableProperty(name = "dcmStorageFileCacheJournalMaxEntries", defaultValue = "100")
    private int journalMaxEntries = 100;

    @ConfigurableProperty(name = "dcmStorageFileCacheAlgorithm", defaultValue = "FIFO")
    private Algorithm cacheAlgorithm = Algorithm.FIFO;

    @ConfigurableProperty(name = "dcmStorageMinFreeSpace")
    private String minFreeSpace;

    private long minFreeSpaceInBytes = -1L;
    private FileCacheProvider fileCacheProvider;

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getFileCacheRootDirectory() {
        return fileCacheRootDirectory;
    }

    public void setFileCacheRootDirectory(String fileCacheRootDirectory) {
        this.fileCacheRootDirectory = fileCacheRootDirectory;
    }

    public String getJournalRootDirectory() {
        return journalRootDirectory;
    }

    public void setJournalRootDirectory(String journalRootDirectory) {
        this.journalRootDirectory = journalRootDirectory;
    }

    public String getJournalFileName() {
        return journalFileName;
    }

    public void setJournalFileName(String journalFileName) {
        this.journalFileName = journalFileName;
    }

    public String getOrphanedFileName() {
        return orphanedFileName;
    }

    public void setOrphanedFileName(String orphanedFileName) {
        this.orphanedFileName = orphanedFileName;
    }

    public String getJournalDirectoryName() {
        return journalDirectoryName;
    }

    public void setJournalDirectoryName(String journalDirectoryName) {
        this.journalDirectoryName = journalDirectoryName;
    }

    public String getJournalFileNamePattern() {
        return journalFileNamePattern;
    }

    public void setJournalFileNamePattern(String journalFileNamePattern) {
        this.journalFileNamePattern = journalFileNamePattern;
    }

    public int getJournalMaxEntries() {
        return journalMaxEntries;
    }

    public void setJournalMaxEntries(int journalMaxEntries) {
        this.journalMaxEntries = journalMaxEntries;
    }

    public Algorithm getCacheAlgorithm() {
        return cacheAlgorithm;
    }

    public void setCacheAlgorithm(Algorithm cacheAlgorithm) {
        this.cacheAlgorithm = cacheAlgorithm;
    }

    public void setMinFreeSpace(String minFreeSpace) {
        this.minFreeSpaceInBytes = minFreeSpace != null
                ? Utils.parseByteSize(minFreeSpace)
                : -1L;
        this.minFreeSpace = minFreeSpace;
    }

    public long getMinFreeSpaceInBytes() {
        return minFreeSpaceInBytes;
    }

    public FileCacheProvider getFileCacheProvider(
            Instance<FileCacheProvider> instances) {
        if (fileCacheProvider == null) {
            FileCacheProvider provider = instances.select(
                    new NamedQualifier(providerName)).get();
            provider.init(this);
            fileCacheProvider = provider;
        }
        return fileCacheProvider;
    }

    public String getMinFreeSpace() {
        return minFreeSpace;
    }

}
