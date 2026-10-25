/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard.backup;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** Runtime optional-app check; release first, development build for local testing. */
final class PetalBackupRestore {
    private static final String[] PACKAGES = {"org.petal.backup", "org.petal.backup.dev"};

    private PetalBackupRestore() {}

    static Intent findRestoreIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : PACKAGES) {
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) continue;
            ResolveInfo resolved = pm.resolveActivity(intent, 0);
            ActivityInfo activity = resolved == null ? null : resolved.activityInfo;
            if (activity == null || !activity.exported || !activity.enabled
                    || activity.applicationInfo == null || !activity.applicationInfo.enabled) {
                continue;
            }
            if (activity.permission != null && context.checkSelfPermission(activity.permission)
                    != PackageManager.PERMISSION_GRANTED) continue;
            // The wizard needs the activity result; launcher NEW_TASK would cancel it immediately.
            return new Intent(intent).setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return null;
    }
}
