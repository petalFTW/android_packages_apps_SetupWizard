/*
 * SPDX-FileCopyrightText: 2019-2020 The Calyx Institute
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.backup;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_ACTIVITY_NOT_FOUND;
import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;

import org.lineageos.setupwizard.R;
import org.lineageos.setupwizard.SubBaseActivity;

public class RestoreIntroActivity extends SubBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;
        getGlifLayout().setDescriptionText(R.string.petal_intro_restore_subtitle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing() && PetalBackupRestore.findRestoreIntent(this) == null) {
            finishAction(RESULT_SKIP);
        }
    }

    @Override
    protected void onSubactivityResult(ActivityResult activityResult) {
        int resultCode = activityResult.getResultCode();
        Intent data = activityResult.getData();
        if (resultCode != RESULT_CANCELED) {
            nextAction(resultCode, data);
        } else if (mIsSubactivityNotFound) {
            finishAction(RESULT_ACTIVITY_NOT_FOUND);
        } else if (data != null && data.getBooleanExtra("onBackPressed", false)) {
            onStartSubactivity();
        }
    }

    @Override
    protected void onStartSubactivity() {
        if (PetalBackupRestore.findRestoreIntent(this) == null) {
            finishAction(RESULT_SKIP);
            return;
        }
        setNextAllowed(true);
    }

    @Override
    protected void onNextPressed() {
        launchRestore();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.intro_restore_activity;
    }

    @Override
    protected int getTitleResId() {
        return R.string.petal_intro_restore_title;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_restore;
    }

    private void launchRestore() {
        Intent intent = PetalBackupRestore.findRestoreIntent(this);
        if (intent == null) {
            finishAction(RESULT_SKIP);
            return;
        }
        try {
            startSubactivity(intent);
        } catch (SecurityException unavailable) {
            finishAction(RESULT_SKIP);
        }
    }

}
