package org.dcm4chee.storage.conf;

/**
 * Policy defining when file's content or metadata should be written
 * synchronously to the underlying storage device.
 *
 * Created by Umberto Cappellini on 10/9/15.
 */
public enum SyncPolicy {

    /**
     * every update to the file's content or metadata is written
     * synchronously to the underlying storage device.
     */
    ALWAYS,

    /**
     * files are synced after store response is sent to the client
     */
    AFTER_STORE_RSP,

    /**
     * files are synced every 5 stores
     */
    EVERY_5_STORE,

    /**
     * files are synced every 25 stores
     */
    EVERY_25_STORE,

    /**
     * files are synced on association close
     */
    ON_ASSOCIATION_CLOSE,

    /*
     * files are synced on storage commitment
     */
    ON_STORAGE_COMMITMENT,

    /*
     * never sync files (and let the underlying storage device deal with it)
     */
    NEVER
}
