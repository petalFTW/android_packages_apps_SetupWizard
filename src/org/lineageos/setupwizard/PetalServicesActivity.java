/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.app.AlertDialog;
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

/** Google Play from the ROM, petal microG from the ROM, or official microG by hand later. */
public class PetalServicesActivity extends BaseSetupWizardActivity {

    static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_STORE = "com.android.vending";
    private static final String PACKAGE_GSF = "com.google.android.gsf";
    private static final File BUNDLE = new File("/product/etc/petal/services/google");
    private static final int GMS_APK_COUNT = 11;
    // Shared with Hub, so its Google Play and microG page opens on the same choice.
    static final String KEY_PROVIDER = "petal_services_setup_provider";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_MICROG = "microg";
    // which microG, Hub reads this too
    static final String KEY_MICROG_FLAVOR = "petal_services_microg_flavor";
    static final String FLAVOR_OFFICIAL = "official";
    static final String FLAVOR_PETAL = "petal";
    private static final File MICROG_BUNDLE = new File("/product/etc/petal/services/microg");

    private RadioGroup mChoice;
    private TextView mStatus;
    private ProgressBar mProgress;
    private PetalInstaller mInstaller;
    private boolean mBusy;
    private boolean mFailed;
    private String mFlavor;
    private int mLastChecked = View.NO_ID;
    private boolean mReverting;

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
            if (mReverting) return;
            if (id != R.id.petal_services_microg) mLastChecked = id;
            mFailed = false;
            mStatus.setVisibility(View.GONE);
            setNextText(R.string.next);
            setNextAllowed(true);
        });
        // click not checked-change, so tapping an already-ticked microG still lets you swap flavor
        findViewById(R.id.petal_services_microg).setOnClickListener(v -> pickFlavor());
        mInstaller = new PetalInstaller(this, this::onInstalled);
        mInstaller.register();
    }

    @Override
    protected void onDestroy() {
        mInstaller.unregister();
        super.onDestroy();
    }

    private void fill(int viewId, int titleId, int summaryId) {
        ((RadioButton) findViewById(viewId)).setText(twoLine(titleId, summaryId));
    }

    // ask every time microG gets ticked, backing out puts the old tick back
    private void pickFlavor() {
        CharSequence[] items = {
                twoLine(R.string.petal_microg_official, R.string.petal_microg_official_summary),
                twoLine(R.string.petal_microg_petal, R.string.petal_microg_petal_summary),
        };
        int[] picked = {FLAVOR_PETAL.equals(mFlavor) ? 1 : FLAVOR_OFFICIAL.equals(mFlavor) ? 0 : -1};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.petal_microg_flavor_title)
                .setSingleChoiceItems(items, picked[0], (d, which) -> {
                    picked[0] = which;
                    ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                })
                .setPositiveButton(R.string.petal_microg_pick, (d, which) -> {
                    mFlavor = picked[0] == 1 ? FLAVOR_PETAL : FLAVOR_OFFICIAL;
                    mLastChecked = R.id.petal_services_microg;
                    fill(R.id.petal_services_microg, R.string.petal_services_microg,
                            FLAVOR_PETAL.equals(mFlavor)
                                    ? R.string.petal_services_microg_petal_picked
                                    : R.string.petal_services_microg_official_picked);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (mLastChecked != R.id.petal_services_microg) revertChoice();
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(picked[0] >= 0);
    }

    private void revertChoice() {
        if (mChoice.getCheckedRadioButtonId() == mLastChecked) return;
        mReverting = true;
        if (mLastChecked == View.NO_ID) mChoice.clearCheck();
        else mChoice.check(mLastChecked);
        mReverting = false;
        setNextAllowed(mLastChecked != View.NO_ID);
    }

    private CharSequence twoLine(int titleId, int summaryId) {
        String title = getString(titleId);
        SpannableString text = new SpannableString(title + "\n" + getString(summaryId));
        text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    @Override
    protected boolean isBusy() {
        return mBusy;
    }

    @Override
    protected void onNextPressed() {
        if (mBusy) return;
        boolean google = mChoice.getCheckedRadioButtonId() == R.id.petal_services_google;
        boolean petal = !google && FLAVOR_PETAL.equals(mFlavor);
        if (mFailed) {
            // Neither happened, so there's nothing to record or to hint at later.
            SetupWizardApp.getSettingsBundle().remove(KEY_PROVIDER);
            super.onNextPressed();
            return;
        }
        if (google ? isGoogleInstalled() : !petal || isPetalMicrogInstalled()) {
            record(google ? PROVIDER_GOOGLE : PROVIDER_MICROG);
            super.onNextPressed();
            return;
        }
        Map<String, File[]> packages = google ? bundledGoogle() : bundledPetalMicrog();
        if (packages == null) {
            fail();
            return;
        }
        mBusy = true;
        setControlsEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(google ? R.string.petal_services_installing
                : R.string.petal_services_installing_microg);
        mStatus.setVisibility(View.VISIBLE);
        mInstaller.install(packages);
    }

    private void onInstalled(boolean success, String message) {
        mBusy = false;
        mProgress.setVisibility(View.GONE);
        setControlsEnabled(true);
        boolean google = mChoice.getCheckedRadioButtonId() == R.id.petal_services_google;
        if (success) {
            record(google ? PROVIDER_GOOGLE : PROVIDER_MICROG);
            nextAction(RESULT_OK);
        } else {
            Log.e(TAG, (google ? "Google Play" : "petal microG") + " install failed: " + message);
            fail();
        }
    }

    // Setup must always be able to finish, so a failure only ever costs the choice.
    private void fail() {
        mFailed = true;
        mStatus.setText(mChoice.getCheckedRadioButtonId() == R.id.petal_services_google
                ? R.string.petal_services_failed : R.string.petal_services_failed_microg);
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
        boolean microg = PROVIDER_MICROG.equals(provider);
        SetupWizardApp.getSettingsBundle().putString(KEY_PROVIDER, provider);
        SetupWizardApp.getSettingsBundle().putString(KEY_MICROG_FLAVOR, microg ? mFlavor : null);
        try {
            Settings.System.putString(getContentResolver(), KEY_PROVIDER, provider);
            if (microg) Settings.System.putString(getContentResolver(), KEY_MICROG_FLAVOR, mFlavor);
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

    // petal build = signed like the OS itself, no hash to keep in sync
    private boolean isPetalMicrogInstalled() {
        return getPackageManager().checkSignatures("android", PACKAGE_GMS)
                == PackageManager.SIGNATURE_MATCH;
    }

    // gms + companion together, companion needs the same key for microG's settings perms
    private static Map<String, File[]> bundledPetalMicrog() {
        File gms = new File(MICROG_BUNDLE, "PetalMicrogGms.apk");
        File companion = new File(MICROG_BUNDLE, "PetalMicrogCompanion.apk");
        if (!gms.isFile() || !companion.isFile()) return null;
        Map<String, File[]> packages = new LinkedHashMap<>();
        packages.put(PACKAGE_GMS, new File[] {gms});
        packages.put(PACKAGE_STORE, new File[] {companion});
        return packages;
    }

    private static Map<String, File[]> bundledGoogle() {
        File[] gms = new File(BUNDLE, "gms").listFiles(
                file -> file.isFile() && file.getName().endsWith(".apk"));
        File store = new File(BUNDLE, "store/gps.apk");
        File gsf = new File(BUNDLE, "gsf/gsf.apk");
        if (gms == null || gms.length != GMS_APK_COUNT || !store.isFile() || !gsf.isFile()) {
            return null;
        }
        Arrays.sort(gms, (a, b) -> a.getName().compareTo(b.getName()));
        Map<String, File[]> packages = new LinkedHashMap<>();
        packages.put(PACKAGE_GMS, gms);
        packages.put(PACKAGE_STORE, new File[] {store});
        // gms crash loops on 36 without this one
        packages.put(PACKAGE_GSF, new File[] {gsf});
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
