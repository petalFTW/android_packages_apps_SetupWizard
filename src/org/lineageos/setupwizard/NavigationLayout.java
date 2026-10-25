/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.android.setupcompat.template.FooterButtonStyleUtils;

public class NavigationLayout extends RelativeLayout {
    // One set of callbacks, whichever look is selected.
    public interface NavigationBarListener {
        void onNavigateNext();

        void onSkip();

        void onNavigateBack();
    }

    private final Button mNextButton;
    private final Button mSkipButton;
    private final Button mBackButton;

    public NavigationLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        boolean petal = PetalSetup.isEnabled(context);
        View.inflate(context, petal ? R.layout.petal_navigation_layout : R.layout.navigation_layout,
                this);
        mNextButton = findViewById(R.id.navbar_next);
        mSkipButton = findViewById(R.id.navbar_skip);
        mBackButton = findViewById(R.id.navbar_back);
        if (!petal) {
            FooterButtonStyleUtils.applyPrimaryButtonPartnerResource(context, mNextButton, true);
            FooterButtonStyleUtils.applySecondaryButtonPartnerResource(context, mSkipButton, true);
        }

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.NavigationLayout, 0, 0);
        final boolean showSkipButton;
        try {
            showSkipButton = a.getBoolean(
                    R.styleable.NavigationLayout_showSkipButton, false);
        } finally {
            a.recycle();
        }

        if (showSkipButton) {
            mSkipButton.setVisibility(View.VISIBLE);
        }
    }

    public Button getSkipButton() {
        return mSkipButton;
    }

    public Button getNextButton() {
        return mNextButton;
    }

    public void setNavigationBarListener(NavigationBarListener listener) {
        mSkipButton.setOnClickListener(view -> listener.onSkip());
        mNextButton.setOnClickListener(view -> listener.onNavigateNext());
        if (mBackButton != null) {
            mBackButton.setOnClickListener(view -> listener.onNavigateBack());
        }
    }
}
