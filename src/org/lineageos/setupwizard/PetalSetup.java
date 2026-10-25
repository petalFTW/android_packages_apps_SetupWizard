/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;

import org.lineageos.setupwizard.util.SetupWizardUtils;

/** Presentation only; the stock wizard still owns provisioning. */
public final class PetalSetup {
    public static final String EXTRA_PRESENTATION =
            "org.lineageos.setupwizard.PRESENTATION";
    public static final String KEY_USE_PETAL = "petalPresentation";

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
}
