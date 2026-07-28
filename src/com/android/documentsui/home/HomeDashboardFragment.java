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

import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.roots.ProvidersCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One-page Files home dashboard: storage cards, favorites, folders, create.
 */
public class HomeDashboardFragment extends Fragment {
    public static final String TAG = "home_dashboard";

    private StorageUsageRingView mRing;
    private TextView mInternalCapacity;
    private TextView mExternalCapacity;
    private View mExternalCard;
    private TextView mPhoneMemoryDetail;
    private TextView mMemoryCardLabel;
    private TextView mMemoryCardDetail;
    private LinearLayout mFoldersList;

    @Nullable
    private StorageVolumeStats.Snapshot mSnapshot;

    public static HomeDashboardFragment newInstance() {
        return new HomeDashboardFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_dashboard, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRing = view.findViewById(R.id.home_usage_ring);
        mInternalCapacity = view.findViewById(R.id.home_internal_capacity);
        mExternalCapacity = view.findViewById(R.id.home_external_capacity);
        mExternalCard = view.findViewById(R.id.home_card_external);
        mPhoneMemoryDetail = view.findViewById(R.id.home_phone_memory_detail);
        mMemoryCardLabel = view.findViewById(R.id.home_memory_card_label);
        mMemoryCardDetail = view.findViewById(R.id.home_memory_card_detail);
        mFoldersList = view.findViewById(R.id.home_folders_list);

        view.findViewById(R.id.home_menu_button).setOnClickListener(v -> openDrawer());
        view.findViewById(R.id.home_search_button).setOnClickListener(v -> openSearch());
        view.findViewById(R.id.home_card_internal).setOnClickListener(v -> openInternal());
        mExternalCard.setOnClickListener(v -> openExternal());
        view.findViewById(R.id.home_favorite_pictures).setOnClickListener(
                v -> openFavorite(RootInfo.TYPE_IMAGES));
        view.findViewById(R.id.home_favorite_video).setOnClickListener(
                v -> openFavorite(RootInfo.TYPE_VIDEO));
        view.findViewById(R.id.home_favorite_music).setOnClickListener(
                v -> openFavorite(RootInfo.TYPE_AUDIO));
        view.findViewById(R.id.home_create_fab).setOnClickListener(
                v -> HomeCreateBottomSheet.show(getParentFragmentManager()));

        // Make favorite circle itself clickable too.
        bindFavoriteChildClick(view.findViewById(R.id.home_favorite_pictures),
                RootInfo.TYPE_IMAGES);
        bindFavoriteChildClick(view.findViewById(R.id.home_favorite_video), RootInfo.TYPE_VIDEO);
        bindFavoriteChildClick(view.findViewById(R.id.home_favorite_music), RootInfo.TYPE_AUDIO);
    }

    private void bindFavoriteChildClick(View group, int rootType) {
        if (group instanceof ViewGroup) {
            final View circle = ((ViewGroup) group).getChildAt(0);
            if (circle != null) {
                circle.setOnClickListener(v -> openFavorite(rootType));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStorageAsync();
        refreshFolders();
    }

    private FilesActivity requireFilesActivity() {
        return (FilesActivity) requireActivity();
    }

    private void openDrawer() {
        requireFilesActivity().setRootsDrawerOpen(true);
    }

    private void openSearch() {
        final FilesActivity activity = requireFilesActivity();
        activity.hideHomeDashboard();
        activity.getInjector().searchManager.onSearchBarClicked();
    }

    private void openInternal() {
        final FilesActivity activity = requireFilesActivity();
        activity.hideHomeDashboard();
        RootInfo root = findRoot(r -> r.isExternalStorageHome() || r.isExternalStorage());
        if (root == null) {
            root = findRoot(RootInfo::isDownloads);
        }
        if (root != null) {
            activity.getInjector().actions.openRoot(root);
        }
    }

    private void openExternal() {
        if (mSnapshot == null || !mSnapshot.hasExternal) {
            return;
        }
        final FilesActivity activity = requireFilesActivity();
        activity.hideHomeDashboard();
        RootInfo root = findRoot(r -> r.isSd() || r.isUsb());
        if (root == null && mSnapshot.externalUuid != null) {
            final String uuid = mSnapshot.externalUuid;
            root = findRoot(r -> uuid.equals(r.rootId));
        }
        if (root != null) {
            activity.getInjector().actions.openRoot(root);
        }
    }

    private void openFavorite(int derivedType) {
        final FilesActivity activity = requireFilesActivity();
        activity.hideHomeDashboard();
        RootInfo root = findRoot(r -> r.derivedType == derivedType);
        if (root != null) {
            activity.getInjector().actions.openRoot(root);
            return;
        }
        // Fallback: open primary storage / downloads if media root missing.
        root = findRoot(RootInfo::isDownloads);
        if (root != null) {
            activity.getInjector().actions.openRoot(root);
        }
    }

    private void openFolder(RootInfo root) {
        final FilesActivity activity = requireFilesActivity();
        activity.hideHomeDashboard();
        activity.getInjector().actions.openRoot(root);
    }

    private void refreshStorageAsync() {
        final android.content.Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            final StorageVolumeStats.Snapshot snap = StorageVolumeStats.load(appContext);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> bindStorage(snap));
        }, "home-storage-stats").start();
    }

    private void bindStorage(StorageVolumeStats.Snapshot snap) {
        mSnapshot = snap;
        final android.content.Context context = getContext();
        if (context == null) {
            return;
        }

        mInternalCapacity.setText(formatSize(context, snap.internalTotalBytes));
        mPhoneMemoryDetail.setText(getString(R.string.home_used_free_fmt,
                formatSize(context, snap.internalUsedBytes),
                formatSize(context, Math.max(0L,
                        snap.internalTotalBytes - snap.internalUsedBytes))));

        if (snap.hasExternal && snap.externalTotalBytes > 0) {
            mExternalCard.setVisibility(View.VISIBLE);
            mExternalCapacity.setText(formatSize(context, snap.externalTotalBytes));
            mMemoryCardLabel.setVisibility(View.VISIBLE);
            mMemoryCardDetail.setVisibility(View.VISIBLE);
            mMemoryCardDetail.setText(getString(R.string.home_used_free_fmt,
                    formatSize(context, snap.externalUsedBytes),
                    formatSize(context, Math.max(0L,
                            snap.externalTotalBytes - snap.externalUsedBytes))));
        } else {
            mExternalCard.setVisibility(View.GONE);
            mMemoryCardLabel.setVisibility(View.GONE);
            mMemoryCardDetail.setVisibility(View.GONE);
        }

        final long total = snap.internalTotalBytes + snap.externalTotalBytes;
        mRing.setUsage(snap.internalUsedBytes, snap.externalUsedBytes, total);
    }

    private void refreshFolders() {
        mFoldersList.removeAllViews();
        final FilesActivity activity = requireFilesActivity();
        final ProvidersCache providers = DocumentsApplication.getProvidersCache(activity);
        final State state = activity.getDisplayState();
        final Collection<RootInfo> roots = providers.getMatchingRootsBlocking(state);

        final List<RootInfo> folders = new ArrayList<>();
        for (RootInfo root : roots) {
            if (root.isRecents() || root.isImages() || root.isVideos() || root.isAudio()) {
                continue;
            }
            if (root.isDownloads() || root.isDocuments() || root.isExternalStorageHome()
                    || root.isExternalStorage() || root.isSd() || root.isUsb()) {
                folders.add(root);
            }
        }

        // Cap list length for the home page.
        final int limit = Math.min(folders.size(), 8);
        final LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < limit; i++) {
            final RootInfo root = folders.get(i);
            final View row = inflater.inflate(R.layout.item_home_folder_row, mFoldersList, false);
            final TextView title = row.findViewById(R.id.home_folder_title);
            final ImageView icon = row.findViewById(R.id.home_folder_icon);
            title.setText(root.title);
            try {
                icon.setImageDrawable(root.loadIcon(activity, false));
            } catch (RuntimeException e) {
                icon.setImageResource(R.drawable.ic_doc_folder);
            }
            row.setOnClickListener(v -> openFolder(root));
            mFoldersList.addView(row);
        }
    }

    @Nullable
    private RootInfo findRoot(java.util.function.Predicate<RootInfo> predicate) {
        final FilesActivity activity = requireFilesActivity();
        final ProvidersCache providers = DocumentsApplication.getProvidersCache(activity);
        final Collection<RootInfo> roots =
                providers.getMatchingRootsBlocking(activity.getDisplayState());
        for (RootInfo root : roots) {
            if (predicate.test(root)) {
                return root;
            }
        }
        // Also try default user roots without mime filtering.
        for (RootInfo root : providers.getRootsBlocking()) {
            if (UserId.DEFAULT_USER.equals(root.userId) && predicate.test(root)) {
                return root;
            }
        }
        return null;
    }

    private static String formatSize(android.content.Context context, long bytes) {
        if (bytes <= 0) {
            return context.getString(R.string.home_storage_unavailable);
        }
        return Formatter.formatShortFileSize(context, bytes);
    }
}
