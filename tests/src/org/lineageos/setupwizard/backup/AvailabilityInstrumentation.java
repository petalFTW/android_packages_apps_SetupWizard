/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard.backup;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.test.mock.MockPackageManager;

import java.util.HashMap;
import java.util.Map;

/** Checks the production availability decision without entering or resetting OOBE. */
public final class AvailabilityInstrumentation extends Instrumentation {
    private static final String RELEASE = "org.petal.backup";
    private static final String DEV = "org.petal.backup.dev";
    private int checks;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyAvailability();
            result.putString("stream", "PASS: " + checks + " optional-backup checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyAvailability() {
        FakePackages packages = new FakePackages();
        TestContext context = new TestContext(getTargetContext(), packages);
        check(PetalBackupRestore.findRestoreIntent(context) == null, "absent packages skip");

        ActivityInfo dev = packages.add(DEV);
        Intent selected = PetalBackupRestore.findRestoreIntent(context);
        check(DEV.equals(selected.getComponent().getPackageName()), "development install is usable");
        check((selected.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0,
                "restore must return a result rather than open a new task");
        check((selected.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0,
                "unrelated launch flags are retained");
        check((packages.lastLaunch.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
                "availability does not mutate the package manager's intent");

        ActivityInfo release = packages.add(RELEASE);
        check(RELEASE.equals(PetalBackupRestore.findRestoreIntent(context)
                .getComponent().getPackageName()), "release wins when both are installed");
        release.enabled = false;
        check(DEV.equals(PetalBackupRestore.findRestoreIntent(context)
                .getComponent().getPackageName()), "disabled release falls back to development");
        dev.enabled = false;
        check(PetalBackupRestore.findRestoreIntent(context) == null, "disabled activities skip");

        release.enabled = true;
        release.applicationInfo.enabled = false;
        check(PetalBackupRestore.findRestoreIntent(context) == null, "disabled application skips");
        release.applicationInfo.enabled = true;
        release.exported = false;
        check(PetalBackupRestore.findRestoreIntent(context) == null, "private activity skips");
        release.exported = true;
        release.permission = "test.permission.RESTORE";
        context.permissionGranted = false;
        check(PetalBackupRestore.findRestoreIntent(context) == null, "inaccessible activity skips");
        context.permissionGranted = true;
        check(PetalBackupRestore.findRestoreIntent(context) != null,
                "accessible protected activity is usable");
        packages.activities.remove(RELEASE);
        check(PetalBackupRestore.findRestoreIntent(context) == null,
                "removal is observed on the next check");
        packages.add(RELEASE);
        check(PetalBackupRestore.findRestoreIntent(context) != null,
                "installation is observed without a wizard rebuild");
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class TestContext extends ContextWrapper {
        private final PackageManager packages;
        boolean permissionGranted = true;

        TestContext(Context base, PackageManager packages) {
            super(base);
            this.packages = packages;
        }

        @Override public PackageManager getPackageManager() { return packages; }
        @Override public int checkSelfPermission(String permission) {
            return permissionGranted ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED;
        }
    }

    private static final class FakePackages extends MockPackageManager {
        final Map<String, ActivityInfo> activities = new HashMap<>();
        Intent lastLaunch;

        ActivityInfo add(String packageName) {
            ActivityInfo activity = new ActivityInfo();
            activity.packageName = packageName;
            activity.name = "org.petal.backup.MainActivity";
            activity.enabled = true;
            activity.exported = true;
            activity.applicationInfo = new ApplicationInfo();
            activity.applicationInfo.enabled = true;
            activities.put(packageName, activity);
            return activity;
        }

        @Override public Intent getLaunchIntentForPackage(String packageName) {
            ActivityInfo activity = activities.get(packageName);
            if (activity == null) return null;
            lastLaunch = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(packageName, activity.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return lastLaunch;
        }

        @Override public ResolveInfo resolveActivity(Intent intent, int flags) {
            ActivityInfo activity = activities.get(intent.getComponent().getPackageName());
            if (activity == null) return null;
            ResolveInfo resolved = new ResolveInfo();
            resolved.activityInfo = activity;
            return resolved;
        }
    }
}
