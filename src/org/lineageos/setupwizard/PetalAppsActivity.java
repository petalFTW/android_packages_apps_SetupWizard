/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** The App Center payload as optional ticks. All off; Hub can add them any time. */
public class PetalAppsActivity extends BaseSetupWizardActivity {

    private static final File PAYLOAD = new File("/system_ext/etc/petal/appcenter");

    // Listed by name, so an APK this build can't parse still shows up instead of vanishing.
    private static final Map<String, Known> KNOWN = Map.of(
            "PetalAssistant.apk", new Known("org.petalos.assistant",
                    R.string.petal_app_assistant_title, R.string.petal_app_assistant),
            "PetalTuner.apk", new Known("org.petalos.petaltuner",
                    R.string.petal_app_tuner_title, R.string.petal_app_tuner),
            "PetalAgeVer.apk", new Known("org.petalos.agever",
                    R.string.petal_app_idhelper_title, R.string.petal_app_idhelper));

    private record Known(String packageName, int title, int summary) {
    }

    private final Map<CheckBox, Map.Entry<String, File>> mApps = new LinkedHashMap<>();
    private LinearLayout mList;
    private TextView mStatus;
    private ProgressBar mProgress;
    private PetalInstaller mInstaller;
    private boolean mBusy;
    private boolean mFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getGlifLayout().setDescriptionText(R.string.petal_apps_summary);
        mList = findViewById(R.id.petal_apps_list);
        mStatus = findViewById(R.id.petal_apps_status);
        mProgress = findViewById(R.id.petal_apps_progress);
        mInstaller = new PetalInstaller(this, this::onInstalled);
        mInstaller.register();
        setNextText(R.string.next);
        addApps();
        if (mApps.isEmpty()) {
            finishAction(RESULT_SKIP);
        }
    }

    @Override
    protected void onDestroy() {
        mInstaller.unregister();
        super.onDestroy();
    }

    private void addApps() {
        File[] files = PAYLOAD.listFiles(
                file -> file.isFile() && file.getName().endsWith(".apk"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        PackageManager pm = getPackageManager();
        LayoutInflater inflater = getLayoutInflater();
        int size = getResources().getDimensionPixelSize(R.dimen.petal_app_icon);
        for (File file : files) {
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            Known known = KNOWN.get(file.getName());
            if (known == null && (info == null || info.applicationInfo == null)) continue;

            CheckBox row = (CheckBox) inflater.inflate(R.layout.petal_app_row, mList, false);
            String title = known != null ? getString(known.title)
                    : String.valueOf(info.applicationInfo.loadLabel(pm));
            SpannableString text = new SpannableString(
                    known == null ? title : title + "\n" + getString(known.summary));
            text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setText(text);

            // Large text needs the row's full width more than it needs an icon.
            if (getResources().getConfiguration().fontScale < 1.3f) {
                Drawable icon = untinted(iconOf(pm, info, file));
                icon.setBounds(0, 0, size, size);
                row.setCompoundDrawablesRelative(row.getCompoundDrawablesRelative()[0], null,
                        icon, null);
            }
            row.setOnCheckedChangeListener((button, checked) -> onTicksChanged());
            mList.addView(row);
            mApps.put(row, Map.entry(known != null ? known.packageName : info.packageName, file));
        }
    }

    // The row tints its compound drawables for the tick box; an app icon must stay as it is.
    private static Drawable untinted(Drawable icon) {
        return new InsetDrawable(icon, 0) {
            @Override
            public void setTintList(ColorStateList tint) {
            }
        };
    }

    private Drawable iconOf(PackageManager pm, PackageInfo info, File file) {
        if (info == null || info.applicationInfo == null) {
            return getDrawable(R.drawable.petal_mark);
        }
        info.applicationInfo.sourceDir = file.getAbsolutePath();
        info.applicationInfo.publicSourceDir = file.getAbsolutePath();
        return info.applicationInfo.loadIcon(pm);
    }

    private void onTicksChanged() {
        mFailed = false;
        mStatus.setVisibility(View.GONE);
        setNextText(ticked().isEmpty() ? R.string.next : R.string.petal_apps_install);
    }

    private Map<String, File> ticked() {
        Map<String, File> chosen = new LinkedHashMap<>();
        for (Map.Entry<CheckBox, Map.Entry<String, File>> app : mApps.entrySet()) {
            if (app.getKey().isChecked()) {
                chosen.put(app.getValue().getKey(), app.getValue().getValue());
            }
        }
        return chosen;
    }

    @Override
    protected boolean isBusy() {
        return mBusy;
    }

    @Override
    protected void onNextPressed() {
        if (mBusy) return;
        Map<String, File> chosen = ticked();
        if (chosen.isEmpty() || mFailed) {
            super.onNextPressed();
            return;
        }
        Map<String, File[]> packages = new LinkedHashMap<>();
        chosen.forEach((name, apk) -> packages.put(name, new File[] {apk}));
        mBusy = true;
        setControlsEnabled(false);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.petal_apps_installing);
        mStatus.setVisibility(View.VISIBLE);
        mInstaller.install(packages);
    }

    private void onInstalled(boolean success, String message) {
        mBusy = false;
        mProgress.setVisibility(View.GONE);
        setControlsEnabled(true);
        if (success) {
            nextAction(RESULT_OK);
            return;
        }
        Log.e(TAG, "App install failed: " + message);
        mFailed = true;
        mStatus.setText(R.string.petal_apps_failed);
        setNextText(R.string.petal_continue_without);
    }

    private void setControlsEnabled(boolean enabled) {
        setNextAllowed(enabled);
        for (CheckBox row : mApps.keySet()) {
            row.setEnabled(enabled);
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.petal_apps;
    }

    @Override
    protected int getTitleResId() {
        return R.string.petal_apps_title;
    }
}
