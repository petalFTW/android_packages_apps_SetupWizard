/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.setupdesign.GlifLayout;

import org.lineageos.setupwizard.backup.RestoreIntroActivity;
import org.lineageos.setupwizard.util.SetupWizardUtils;
import org.lineageos.setupwizard.widget.PetalStepDrawable;

/** Presentation only; the stock wizard still owns provisioning. */
public final class PetalSetup {
    public static final String EXTRA_PRESENTATION =
            "org.lineageos.setupwizard.PRESENTATION";
    public static final String KEY_USE_PETAL = "petalPresentation";
    private static final String TAG = "PetalSetup";
    private static final int STEPS = 7;
    private static final int PETALS = 3;
    // How many logo petals are filled at each step; index 0 is unused.
    private static final int[] PETALS_AT_STEP = {PETALS, 1, 1, 2, 2, 2, 3, 3};

    private PetalSetup() {}

    public static boolean isEnabled(Context context) {
        if (SetupWizardUtils.hasLeanback(context)
                || context.getResources().getBoolean(R.bool.config_isLargeNoTouch)) {
            return false;
        }
        Context owner = context;
        while (owner instanceof ContextWrapper && !(owner instanceof Activity)) {
            owner = ((ContextWrapper) owner).getBaseContext();
        }
        if (owner instanceof Activity) {
            Intent intent = ((Activity) owner).getIntent();
            if (intent != null) {
                Bundle wizard = intent.getBundleExtra(SetupWizardApp.EXTRA_WIZARD_BUNDLE);
                if (wizard != null && wizard.containsKey(KEY_USE_PETAL)) {
                    return wizard.getBoolean(KEY_USE_PETAL);
                }
                String requested = intent.getStringExtra(EXTRA_PRESENTATION);
                if ("stock".equals(requested)) return false;
                if ("petal".equals(requested)) return true;
            }
        }
        return context.getResources().getBoolean(R.bool.config_use_petal_setup);
    }

    public static void copySelection(Intent source, Bundle target) {
        if (source == null) return;
        Bundle wizard = source.getBundleExtra(SetupWizardApp.EXTRA_WIZARD_BUNDLE);
        if (wizard != null && wizard.containsKey(KEY_USE_PETAL)) {
            target.putBoolean(KEY_USE_PETAL, wizard.getBoolean(KEY_USE_PETAL));
        }
    }

    /** The number of steps in this run, or 0 when it isn't the owner's short route. */
    public static int stepCount(Activity activity) {
        Bundle wizard = activity.getIntent().getBundleExtra(SetupWizardApp.EXTRA_WIZARD_BUNDLE);
        String script = wizard == null ? null : wizard.getString(SetupWizardApp.EXTRA_SCRIPT_URI);
        return activity.getString(R.string.petal_wizard_script_uri).equals(script) ? STEPS : 0;
    }

    private static int stepOf(Activity activity) {
        if (activity instanceof LocaleActivity) return 1;
        if (activity instanceof SimMissingActivity
                || activity instanceof NetworkSetupActivity) return 2;
        if (activity instanceof DateTimeActivity
                || activity instanceof RestoreIntroActivity) return 3;
        if (activity instanceof BiometricActivity
                || activity instanceof ScreenLockActivity) return 4;
        if (activity instanceof LocationSettingsActivity) return 5;
        if (activity instanceof PetalServicesActivity) return 6;
        if (activity instanceof PetalAppsActivity) return 7;
        return 0;
    }

    /** Dresses a stock Glif page. Anything going wrong here leaves that page plain. */
    static void dressPage(Activity activity) {
        View view = activity.findViewById(R.id.setup_wizard_layout);
        if (!(view instanceof GlifLayout)) return;
        GlifLayout layout = (GlifLayout) view;
        try {
            layout.setBackgroundPatterned(false);
            layout.setBackgroundBaseColor(
                    ColorStateList.valueOf(activity.getColor(R.color.petal_setup_surface)));
            int step = stepOf(activity);
            boolean counted = step != 0 && stepCount(activity) != 0;
            layout.setIcon(new PetalStepDrawable(activity, PETALS_AT_STEP[counted ? step : 0]));
            if (counted) addStepLabel(activity, layout, step);
            TextView header = layout.getHeaderTextView();
            if (header != null) {
                header.setTextSize(30);
                header.setTextColor(activity.getColor(R.color.petal_setup_text));
                header.setTypeface(Typeface.create(header.getTypeface(), 600, false));
            }
            TextView description = layout.getDescriptionTextView();
            if (description != null) {
                description.setTextSize(15);
                description.setTextColor(activity.getColor(R.color.petal_setup_secondary));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Couldn't dress " + activity.getClass().getSimpleName(), e);
        }
    }

    private static void addStepLabel(Activity activity, GlifLayout layout, int step) {
        FrameLayout icons = layout.findViewById(
                com.google.android.setupdesign.R.id.sud_layout_icon_container);
        if (icons == null) return;
        TextView label = new TextView(activity);
        label.setText(activity.getString(R.string.petal_step_of, step, STEPS));
        label.setTextSize(14);
        label.setTextColor(activity.getColor(R.color.petal_setup_secondary));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        icons.addView(label, params);
    }
}
