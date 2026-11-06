/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installs the ROM's bundled APKs, the way Hub does. Failures are reported, never thrown. */
final class PetalInstaller {
    interface Callback {
        void onFinished(boolean success, String message);
    }

    private static final String ACTION_STATUS = "org.lineageos.setupwizard.PETAL_INSTALL_STATUS";
    // Pages lock their controls while this runs, so silence must not lock them for good.
    private static final long TIMEOUT_MS = 5 * 60 * 1000;

    private final Context mContext;
    private final Callback mCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTimeout = () -> finish(false, "timed out");
    private boolean mRunning;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                if (confirm != null) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
            }
            finish(status == PackageInstaller.STATUS_SUCCESS,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    };

    PetalInstaller(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    void register() {
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_STATUS),
                Context.RECEIVER_NOT_EXPORTED);
    }

    void unregister() {
        mHandler.removeCallbacks(mTimeout);
        mRunning = false;
        mContext.unregisterReceiver(mReceiver);
    }

    private void finish(boolean success, String message) {
        if (!mRunning) return;
        mRunning = false;
        mHandler.removeCallbacks(mTimeout);
        mCallback.onFinished(success, message);
    }

    /** Commits every package together, so a half-installed Google can't exist. */
    void install(Map<String, File[]> packages) {
        mRunning = true;
        mHandler.postDelayed(mTimeout, TIMEOUT_MS);
        new Thread(() -> installAll(new LinkedHashMap<>(packages)), "PetalInstall").start();
    }

    private void installAll(Map<String, File[]> packages) {
        PackageInstaller installer = mContext.getPackageManager().getPackageInstaller();
        List<Integer> sessions = new ArrayList<>();
        try {
            PackageInstaller.SessionParams parentParams = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            parentParams.setMultiPackage();
            int parentId = installer.createSession(parentParams);
            sessions.add(parentId);
            try (PackageInstaller.Session parent = installer.openSession(parentId)) {
                for (Map.Entry<String, File[]> entry : packages.entrySet()) {
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    params.setAppPackageName(entry.getKey());
                    params.setInstallerPackageName(mContext.getPackageName());
                    int id = installer.createSession(params);
                    sessions.add(id);
                    try (PackageInstaller.Session child = installer.openSession(id)) {
                        for (File apk : entry.getValue()) {
                            write(child, apk);
                        }
                    }
                    parent.addChildSessionId(id);
                }
                if (packages.containsKey(PetalServicesActivity.PACKAGE_GMS)) {
                    // Wake the shim before the freshly installed Google process starts.
                    mContext.getPackageManager().setApplicationEnabledSetting(
                            "app.grapheneos.gmscompat",
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
                }
                Intent status = new Intent(ACTION_STATUS).setPackage(mContext.getPackageName());
                parent.commit(PendingIntent.getBroadcast(mContext, parentId, status,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                        .getIntentSender());
            }
        } catch (Exception e) {
            for (int id : sessions) {
                try {
                    installer.abandonSession(id);
                } catch (RuntimeException ignored) {
                }
            }
            mContext.getMainExecutor().execute(() -> finish(false, e.toString()));
        }
    }

    private static void write(PackageInstaller.Session session, File apk) throws Exception {
        try (InputStream input = new FileInputStream(apk);
                OutputStream output = session.openWrite(apk.getName(), 0, apk.length())) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            session.fsync(output);
        }
    }
}
