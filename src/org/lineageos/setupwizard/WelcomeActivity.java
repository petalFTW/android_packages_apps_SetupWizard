/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.ACTION_EMERGENCY_DIAL;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.setupcompat.template.FooterButtonStyleUtils;
import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class WelcomeActivity extends SubBaseActivity {

    private static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS_FOR_SUW";

    @Override
    protected void onStartSubactivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onSetupStart();
        SystemBarHelper.setBackButtonVisible(getWindow(), false);
        setNextText(R.string.start);
        Button startButton = findViewById(R.id.start);
        Button emergButton = findViewById(R.id.emerg_dialer);
        Button skipButton = findViewById(R.id.skip);
        startButton.setOnClickListener(view -> onNextPressed());
        findViewById(R.id.launch_accessibility)
                .setOnClickListener(
                        view -> startSubactivity(new Intent(ACTION_ACCESSIBILITY_SETTINGS)));

        if (isPetalSetup()) {
            getWindow().setDecorFitsSystemWindows(false);
            View root = findViewById(R.id.petal_welcome_root);
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(root);
            Button language = findViewById(R.id.petal_welcome_language);
            language.setText(java.util.Locale.getDefault().getDisplayName());
            language.setOnClickListener(view -> onNextPressed());
        } else {
            FooterButtonStyleUtils.applyPrimaryButtonPartnerResource(this, startButton, true);
        }

        if (SetupWizardUtils.hasTelephony(this)) {
            setSkipText(R.string.emergency_call);
            emergButton.setOnClickListener(
                    view -> startSubactivity(new Intent(ACTION_EMERGENCY_DIAL)));

            if (!isPetalSetup()) {
                FooterButtonStyleUtils.applySecondaryButtonPartnerResource(this, emergButton, true);
            }
        } else {
            emergButton.setVisibility(View.GONE);
        }

        TextView welcomeTitle = findViewById(R.id.welcome_title);
        if (SetupWizardUtils.isManagedProfile(this)) {
            welcomeTitle.setText(getString(R.string.setup_managed_profile_welcome_message));
            if (isPetalSetup()) welcomeTitle.setTextSize(32);
        } else if (isPetalSetup()) {
            welcomeTitle.setText(R.string.petal_hello);
        } else {
            welcomeTitle.setText(getString(R.string.setup_welcome_message,
                    getString(R.string.os_name)));
        }

        if (Build.TYPE.equals("eng")) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> {
                SetupWizardUtils.finishSetupWizard(WelcomeActivity.this);
            });
        }
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.welcome_activity;
    }

    @Override
    protected int getTitleResId() {
        return -1;
    }
}
