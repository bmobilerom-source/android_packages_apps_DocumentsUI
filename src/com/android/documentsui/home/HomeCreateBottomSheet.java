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

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.base.SharedMinimal.TAG;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.CreateDirectoryFragment;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.Metrics;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.ui.Snackbars;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

/**
 * Create folder or empty text document from the Files home dashboard.
 */
public class HomeCreateBottomSheet extends BottomSheetDialogFragment {
    private static final String TAG_SHEET = "home_create_sheet";

    public static void show(FragmentManager fm) {
        if (fm.isStateSaved()) {
            return;
        }
        new HomeCreateBottomSheet().show(fm, TAG_SHEET);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_home_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final EditText nameField = view.findViewById(R.id.home_create_name);
        final Chip folderChip = view.findViewById(R.id.home_create_type_folder);
        final Chip textChip = view.findViewById(R.id.home_create_type_text);

        view.findViewById(R.id.home_create_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.home_create_confirm).setOnClickListener(v -> {
            final String name = nameField.getText() != null
                    ? nameField.getText().toString().trim() : "";
            if (name.isEmpty()) {
                nameField.setError(getString(R.string.add_folder_name_error));
                return;
            }
            if (folderChip.isChecked()) {
                dismiss();
                createFolder(name);
            } else if (textChip.isChecked()) {
                dismiss();
                createTextDocument(name);
            }
        });
    }

    private void createFolder(String name) {
        final FilesActivity activity = (FilesActivity) requireActivity();
        activity.hideHomeDashboard();
        final DocumentInfo cwd = activity.getCurrentDirectory();
        if (cwd == null) {
            CreateDirectoryFragment.show(activity.getSupportFragmentManager());
            return;
        }
        new CreateNamedDirectoryTask(activity, cwd, name)
                .executeOnExecutor(ProviderExecutor.forAuthority(cwd.authority));
    }

    private void createTextDocument(String name) {
        final FilesActivity activity = (FilesActivity) requireActivity();
        activity.hideHomeDashboard();
        final DocumentInfo cwd = activity.getCurrentDirectory();
        if (cwd == null) {
            Snackbars.makeSnackbar(activity, R.string.create_error, Snackbar.LENGTH_LONG).show();
            return;
        }
        String displayName = name;
        if (!displayName.contains(".")) {
            displayName = displayName + ".txt";
        }
        new CreateTextDocumentTask(activity, cwd, displayName)
                .executeOnExecutor(ProviderExecutor.forAuthority(cwd.authority));
    }

    private static final class CreateNamedDirectoryTask
            extends AsyncTask<Void, Void, DocumentInfo> {
        private final FilesActivity mActivity;
        private final DocumentInfo mCwd;
        private final String mDisplayName;

        CreateNamedDirectoryTask(FilesActivity activity, DocumentInfo cwd, String displayName) {
            mActivity = activity;
            mCwd = cwd;
            mDisplayName = displayName;
        }

        @Override
        protected DocumentInfo doInBackground(Void... params) {
            final ContentResolver resolver = mCwd.userId.getContentResolver(mActivity);
            ContentProviderClient client = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        resolver, mCwd.derivedUri.getAuthority());
                final Uri childUri = DocumentsContract.createDocument(
                        wrap(client), mCwd.derivedUri, Document.MIME_TYPE_DIR, mDisplayName);
                DocumentInfo doc = DocumentInfo.fromUri(resolver, childUri, mCwd.userId);
                return doc.isDirectory() ? doc : null;
            } catch (Exception e) {
                Log.w(TAG, "Failed to create directory", e);
                return null;
            } finally {
                FileUtils.closeQuietly(client);
            }
        }

        @Override
        protected void onPostExecute(DocumentInfo result) {
            if (result != null) {
                mActivity.onDirectoryCreated(result);
                Metrics.logCreateDirOperation();
            } else {
                Snackbars.makeSnackbar(mActivity, R.string.create_error, Snackbar.LENGTH_LONG)
                        .show();
                Metrics.logCreateDirError();
            }
        }
    }

    private static final class CreateTextDocumentTask extends AsyncTask<Void, Void, DocumentInfo> {
        private final FilesActivity mActivity;
        private final DocumentInfo mCwd;
        private final String mDisplayName;

        CreateTextDocumentTask(FilesActivity activity, DocumentInfo cwd, String displayName) {
            mActivity = activity;
            mCwd = cwd;
            mDisplayName = displayName;
        }

        @Override
        protected DocumentInfo doInBackground(Void... params) {
            final ContentResolver resolver = mCwd.userId.getContentResolver(mActivity);
            ContentProviderClient client = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        resolver, mCwd.derivedUri.getAuthority());
                final Uri childUri = DocumentsContract.createDocument(
                        wrap(client), mCwd.derivedUri, "text/plain", mDisplayName);
                return DocumentInfo.fromUri(resolver, childUri, mCwd.userId);
            } catch (Exception e) {
                Log.w(TAG, "Failed to create text document", e);
                return null;
            } finally {
                FileUtils.closeQuietly(client);
            }
        }

        @Override
        protected void onPostExecute(DocumentInfo result) {
            if (result != null) {
                Snackbars.makeSnackbar(mActivity, R.string.home_create_text_document,
                        Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbars.makeSnackbar(mActivity, R.string.create_error, Snackbar.LENGTH_LONG)
                        .show();
            }
        }
    }
}
