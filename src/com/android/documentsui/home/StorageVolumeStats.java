/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.home;

import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Loads internal/external volume totals using public StorageManager APIs
 * (DocumentsUI builds against system_current, so VolumeInfo is unavailable).
 */
public final class StorageVolumeStats {
    private static final String TAG = "StorageVolumeStats";

    public static final class Snapshot {
        public long internalTotalBytes;
        public long internalUsedBytes;
        public long externalTotalBytes;
        public long externalUsedBytes;
        public boolean hasExternal;

        /** UUID string for the first mounted removable/public volume, if any. */
        @Nullable
        public String externalUuid;
    }

    private StorageVolumeStats() {
    }

    public static Snapshot load(Context context) {
        final Snapshot snap = new Snapshot();
        final StorageManager storageManager = context.getSystemService(StorageManager.class);
        final StorageStatsManager statsManager = context.getSystemService(StorageStatsManager.class);
        if (storageManager == null) {
            return snap;
        }

        // Primary / internal
        final StorageVolume primary = storageManager.getPrimaryStorageVolume();
        fillPrivate(storageManager, statsManager, primary, snap);

        // First non-primary mounted volume (SD / USB / adopted public)
        final List<StorageVolume> volumes = storageManager.getStorageVolumes();
        for (StorageVolume volume : volumes) {
            if (volume.isPrimary()) {
                continue;
            }
            final String state = volume.getState();
            if (!Environment.MEDIA_MOUNTED.equals(state)
                    && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                continue;
            }
            fillPublic(volume, snap);
            if (snap.hasExternal) {
                break;
            }
        }
        return snap;
    }

    private static void fillPrivate(StorageManager storageManager,
            @Nullable StorageStatsManager statsManager, @Nullable StorageVolume primary,
            Snapshot snap) {
        if (statsManager == null) {
            return;
        }
        try {
            final UUID uuid;
            if (primary != null && primary.getDirectory() != null) {
                uuid = storageManager.getUuidForPath(primary.getDirectory());
            } else {
                uuid = StorageManager.UUID_DEFAULT;
            }
            snap.internalTotalBytes = statsManager.getTotalBytes(uuid);
            final long free = statsManager.getFreeBytes(uuid);
            snap.internalUsedBytes = Math.max(0L, snap.internalTotalBytes - free);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed reading primary storage stats", e);
            // Fallback to Environment data directory sizes.
            final File data = Environment.getDataDirectory();
            if (data != null) {
                snap.internalTotalBytes = data.getTotalSpace();
                snap.internalUsedBytes = Math.max(0L,
                        snap.internalTotalBytes - data.getFreeSpace());
            }
        }
    }

    private static void fillPublic(StorageVolume volume, Snapshot snap) {
        final File path = volume.getDirectory();
        if (path == null) {
            return;
        }
        snap.hasExternal = true;
        snap.externalUuid = volume.getUuid();
        snap.externalTotalBytes = path.getTotalSpace();
        final long free = path.getFreeSpace();
        snap.externalUsedBytes = Math.max(0L, snap.externalTotalBytes - free);
    }
}
