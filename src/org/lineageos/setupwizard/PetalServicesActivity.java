/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Google Play from the ROM, or microG by hand later. Nothing is picked for the user. */
public class PetalServicesActivity extends BaseSetupWizardActivity {

    static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_STORE = "com.android.vending";
    private static final File BUNDLE = new File("/product/etc/petal/services/google");
    private static final int GMS_APK_COUNT = 11;
    // Shared with Hub, so its Google Play and microG page opens on the same choice.
    static final String KEY_PROVIDER = "petal_services_setup_provider";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_MICROG = "microg";

    private RadioGroup mChoice;
    private TextView mStatus;
    private ProgressBar mProgress;
    private PetalInstaller mInstaller;
    private boolean mBusy;
    private boolean mFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getGlifLayout().setDescriptionText(R.string.petal_services_summary);
        mChoice = findViewById(R.id.petal_services_choice);
        mStatus = findViewById(R.id.petal_services_status);
        mProgress = findViewById(R.id.petal_services_progress);
        fill(R.id.petal_services_google, R.string.petal_services_google,
                R.string.petal_services_google_summary);
        fill(R.id.petal_services_microg, R.string.petal_services_microg,
                R.string.petal_services_microg_summary);
        setNextText(R.string.next);
        setNextAllowed(mChoice.getCheckedRadioButtonId() != View.NO_ID);
        mChoice.setOnCheckedChangeListener((group, id) -> {
            mFailed = false;
            mStatus.setVisibility(View.GONE);
            setNextText(R.string.next);
            setNextAllowed(true);
        });
        mInstaller = new PetalInstaller(this, this::onInstalled);
        mInstaller.register();
    }

    @Override
    protected void onDestroy() {
        mInstaller.unregister();
        super.onDestroy();
    }

    private void fill(int viewId, int titleId, int summaryId) {
        String title = getString(titleId);
        SpannableString text = new SpannableString(title + "\n" + getString(summaryId));
        text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((RadioButton) findViewById(viewId)).setText(text);
    }

    @Override
    protected boolean isBusy() {
        return mBusy;
    }

    @Override
    protected void onNextPressed() {
        if (mBusy) return;
        boolean google = mChoice.getCheckedRadioButtonId() == R.id.petal_services_google;
        if (mFailed) {
            // Neither happened, so there's nothing to record or to hint at later.
            SetupWizardApp.getSettingsBundle().remove(KEY_PROVIDER);
            super.onNextPressed();
            return;
        }
        if (!google || isGoogleInstalled()) {
            record(google ? PROVIDER_GOOGLE : PROVIDER_MICROG);
            super.onNextPressed();
            return;
        }
        Map<String, File[]> packages = bundledGoogle();
        if (packages == null) {
            fail();
            return;
        }
        mBusy = true;
        setControlsEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.petal_services_installing);
        mStatus.setVisibility(View.VISIBLE);
        mInstaller.install(packages);
    }

    private void onInstalled(boolean success, String message) {
        mBusy = false;
        mProgress.setVisibility(View.GONE);
        setControlsEnabled(true);
        if (success) {
            record(PROVIDER_GOOGLE);
            nextAction(RESULT_OK);
        } else {
            Log.e(TAG, "Google Play install failed: " + message);
            fail();
        }
    }

    // Setup must always be able to finish, so a failure only ever costs the choice.
    private void fail() {
        mFailed = true;
        mStatus.setText(R.string.petal_services_failed);
        mStatus.setVisibility(View.VISIBLE);
        setNextText(R.string.petal_continue_without);
    }

    private void setControlsEnabled(boolean enabled) {
        setNextAllowed(enabled);
        for (int i = 0; i < mChoice.getChildCount(); i++) {
            mChoice.getChildAt(i).setEnabled(enabled);
        }
    }

    private void record(String provider) {
        SetupWizardApp.getSettingsBundle().putString(KEY_PROVIDER, provider);
        try {
            Settings.System.putString(getContentResolver(), KEY_PROVIDER, provider);
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't record provider", e);
        }
    }

    private boolean isGoogleInstalled() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(PACKAGE_GMS,
                    PackageManager.GET_META_DATA);
            Bundle metadata = info.applicationInfo.metaData;
            return !(info.getLongVersionCode() == 1 && metadata != null
                    && metadata.getBoolean("org.petalos.services.PLACEHOLDER", false));
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static Map<String, File[]> bundledGoogle() {
        File[] gms = new File(BUNDLE, "gms").listFiles(
                file -> file.isFile() && file.getName().endsWith(".apk"));
        File store = new File(BUNDLE, "store/gps.apk");
        if (gms == null || gms.length != GMS_APK_COUNT || !store.isFile()) return null;
        Arrays.sort(gms, (a, b) -> a.getName().compareTo(b.getName()));
        Map<String, File[]> packages = new LinkedHashMap<>();
        packages.put(PACKAGE_GMS, gms);
        packages.put(PACKAGE_STORE, new File[] {store});
        return packages;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.petal_services;
    }

    @Override
    protected int getTitleResId() {
        return R.string.petal_services_title;
    }
}
