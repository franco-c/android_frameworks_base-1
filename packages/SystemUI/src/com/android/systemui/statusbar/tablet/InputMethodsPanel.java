/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.tablet;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.android.systemui.R;

public class InputMethodsPanel extends LinearLayout implements StatusBarPanel, OnClickListener {
    private static final boolean DEBUG = TabletStatusBar.DEBUG;
    private static final String TAG = "InputMethodsPanel";

    private final InputMethodManager mImm;
    private final HashMap<InputMethodInfo, List<InputMethodSubtype>>
            mEnabledInputMethodAndSubtypesCache =
                    new HashMap<InputMethodInfo, List<InputMethodSubtype>>();
    private final HashMap<View, Pair<InputMethodInfo, InputMethodSubtype>> mRadioViewAndImiMap =
            new HashMap<View, Pair<InputMethodInfo, InputMethodSubtype>>();

    private Context mContext;
    private IBinder mToken;
    private InputMethodButton mInputMethodSwitchButton;
    private LinearLayout mInputMethodMenuList;
    private PackageManager mPackageManager;
    private String mEnabledInputMethodAndSubtypesCacheStr;
    private View mConfigureImeShortcut;

    public InputMethodsPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InputMethodsPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mImm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public void onFinishInflate() {
        mInputMethodMenuList = (LinearLayout) findViewById(R.id.input_method_menu_list);
        mConfigureImeShortcut = ((View) findViewById(R.id.ime_settings_shortcut));
        mConfigureImeShortcut.setOnClickListener(this);
        // TODO: If configurations for IME are not changed, do not update
        // by checking onConfigurationChanged.
        updateUiElements();
    }

    @Override
    public boolean isInContentArea(int x, int y) {
        return false;
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this) {
            if (visibility == View.VISIBLE) {
                updateUiElements();
                if (mInputMethodSwitchButton != null) {
                    mInputMethodSwitchButton.setIconImage(R.drawable.ic_sysbar_ime_pressed);
                }
            } else {
                if (mInputMethodSwitchButton != null) {
                    mInputMethodSwitchButton.setIconImage(R.drawable.ic_sysbar_ime);
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view == mConfigureImeShortcut) {
            showConfigureInputMethods();
            onFinishPanel(true);
            return;
        }
    }

    private void onFinishPanel(boolean closeKeyboard) {
        setVisibility(View.GONE);
        if (closeKeyboard) {
            mImm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    private void startActivity(Intent intent) {
        mContext.startActivity(intent);
    }

    private void showConfigureInputMethods() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private View createInputMethodItem(
            final InputMethodInfo imi, final InputMethodSubtype subtype) {
        CharSequence subtypeName = getSubtypeName(imi, subtype);
        CharSequence imiName = getIMIName(imi);
        Drawable icon = getSubtypeIcon(imi, subtype);
        View view = View.inflate(mContext, R.layout.status_bar_input_methods_item, null);
        ImageView subtypeIcon = (ImageView)view.findViewById(R.id.item_icon);
        TextView itemTitle = (TextView)view.findViewById(R.id.item_title);
        TextView itemSubtitle = (TextView)view.findViewById(R.id.item_subtitle);
        ImageView settingsIcon = (ImageView)view.findViewById(R.id.item_settings_icon);
        View subtypeView = view.findViewById(R.id.item_subtype);
        if (subtypeName == null) {
            itemTitle.setText(imiName);
            itemSubtitle.setVisibility(View.GONE);
        } else {
            itemTitle.setText(subtypeName);
            itemSubtitle.setVisibility(View.VISIBLE);
            itemSubtitle.setText(imiName);
        }
        subtypeIcon.setImageDrawable(icon);
        final String settingsActivity = imi.getSettingsActivity();
        if (!TextUtils.isEmpty(settingsActivity)) {
            settingsIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName(imi.getPackageName(), settingsActivity);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    onFinishPanel(true);
                }
            });
        } else {
            // Do not show the settings icon if the IME does not have a settings preference
            view.findViewById(R.id.item_vertical_separator).setVisibility(View.GONE);
            settingsIcon.setVisibility(View.GONE);
        }
        mRadioViewAndImiMap.put(
                subtypeView, new Pair<InputMethodInfo, InputMethodSubtype> (imi, subtype));
        subtypeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Pair<InputMethodInfo, InputMethodSubtype> imiAndSubtype =
                        updateRadioButtonsByView(view);
                onFinishPanel(false);
                setInputMethodAndSubtype(imiAndSubtype.first, imiAndSubtype.second);
            }
        });
        return view;
    }

    private void updateUiElements() {
        // TODO: Reuse subtype views.
        mInputMethodMenuList.removeAllViews();
        mRadioViewAndImiMap.clear();
        mPackageManager = mContext.getPackageManager();

        HashMap<InputMethodInfo, List<InputMethodSubtype>> enabledIMIs
                = getEnabledInputMethodAndSubtypeList();
        // TODO: Sort by alphabet and mode.
        Set<InputMethodInfo> cachedImiSet = enabledIMIs.keySet();
        for (InputMethodInfo imi: cachedImiSet) {
            List<InputMethodSubtype> subtypes = enabledIMIs.get(imi);
            if (subtypes == null || subtypes.size() == 0) {
                mInputMethodMenuList.addView(
                        createInputMethodItem(imi, null));
                continue;
            }
            for (InputMethodSubtype subtype: subtypes) {
                mInputMethodMenuList.addView(createInputMethodItem(imi, subtype));
            }
        }
        updateRadioButtons();
    }

    public void setImeToken(IBinder token) {
        mToken = token;
    }

    public void setImeSwitchButton(InputMethodButton imb) {
        mInputMethodSwitchButton = imb;
    }

    private void setInputMethodAndSubtype(InputMethodInfo imi, InputMethodSubtype subtype) {
        if (mToken != null) {
            mImm.setInputMethodAndSubtype(mToken, imi.getId(), subtype);
        } else {
            Log.w(TAG, "IME Token is not set yet.");
        }
    }

    // Turn on the selected radio button when the user chooses the item
    private Pair<InputMethodInfo, InputMethodSubtype> updateRadioButtonsByView(View selectedView) {
        Pair<InputMethodInfo, InputMethodSubtype> selectedImiAndSubtype = null;
        if (mRadioViewAndImiMap.containsKey(selectedView)) {
            for (View radioView: mRadioViewAndImiMap.keySet()) {
                RadioButton subtypeRadioButton =
                        (RadioButton) radioView.findViewById(R.id.item_radio);
                if (subtypeRadioButton == null) {
                    Log.w(TAG, "RadioButton was not found in the selected subtype view");
                    return null;
                }
                if (radioView == selectedView) {
                    Pair<InputMethodInfo, InputMethodSubtype> imiAndSubtype =
                        mRadioViewAndImiMap.get(radioView);
                    selectedImiAndSubtype = imiAndSubtype;
                    subtypeRadioButton.setChecked(true);
                } else {
                    subtypeRadioButton.setChecked(false);
                }
            }
        }
        return selectedImiAndSubtype;
    }

    private void updateRadioButtons() {
        updateRadioButtonsByImiAndSubtype(
                getCurrentInputMethodInfo(), mImm.getCurrentInputMethodSubtype());
    }

    // Turn on the selected radio button at startup
    private void updateRadioButtonsByImiAndSubtype(
            InputMethodInfo imi, InputMethodSubtype subtype) {
        if (imi == null) return;
        if (DEBUG) {
            Log.d(TAG, "Update radio buttons by " + imi.getId() + ", " + subtype);
        }
        for (View radioView: mRadioViewAndImiMap.keySet()) {
            RadioButton subtypeRadioButton =
                    (RadioButton) radioView.findViewById(R.id.item_radio);
            if (subtypeRadioButton == null) {
                Log.w(TAG, "RadioButton was not found in the selected subtype view");
                return;
            }
            Pair<InputMethodInfo, InputMethodSubtype> imiAndSubtype =
                    mRadioViewAndImiMap.get(radioView);
            if (imiAndSubtype.first.getId().equals(imi.getId())
                    && (imiAndSubtype.second == null || imiAndSubtype.second.equals(subtype))) {
                subtypeRadioButton.setChecked(true);
            } else {
                subtypeRadioButton.setChecked(false);
            }
        }
    }

    private HashMap<InputMethodInfo, List<InputMethodSubtype>>
            getEnabledInputMethodAndSubtypeList() {
        String newEnabledIMIs = Settings.Secure.getString(
                mContext.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        if (mEnabledInputMethodAndSubtypesCacheStr == null
                || !mEnabledInputMethodAndSubtypesCacheStr.equals(newEnabledIMIs)) {
            mEnabledInputMethodAndSubtypesCache.clear();
            final List<InputMethodInfo> imis = mImm.getEnabledInputMethodList();
            for (InputMethodInfo imi: imis) {
                mEnabledInputMethodAndSubtypesCache.put(imi,
                        mImm.getEnabledInputMethodSubtypeList(imi, true));
            }
            mEnabledInputMethodAndSubtypesCacheStr = newEnabledIMIs;
        }
        return mEnabledInputMethodAndSubtypesCache;
    }

    private InputMethodInfo getCurrentInputMethodInfo() {
        String curInputMethodId = Settings.Secure.getString(getContext()
                .getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        Set<InputMethodInfo> cachedImiSet = mEnabledInputMethodAndSubtypesCache.keySet();
        // 1. Search IMI in cache
        for (InputMethodInfo imi: cachedImiSet) {
            if (imi.getId().equals(curInputMethodId)) {
                return imi;
            }
        }
        // 2. Get current enabled IMEs and search IMI
        cachedImiSet = getEnabledInputMethodAndSubtypeList().keySet();
        for (InputMethodInfo imi: cachedImiSet) {
            if (imi.getId().equals(curInputMethodId)) {
                return imi;
            }
        }
        return null;
    }

    private CharSequence getIMIName(InputMethodInfo imi) {
        if (imi == null) return null;
        return mPackageManager.getApplicationLabel(imi.getServiceInfo().applicationInfo);
    }

    private CharSequence getSubtypeName(InputMethodInfo imi, InputMethodSubtype subtype) {
        if (imi == null || subtype == null) return null;
        if (DEBUG) {
            Log.d(TAG, "Get text from: " + imi.getPackageName() + subtype.getNameResId()
                    + imi.getServiceInfo().applicationInfo);
        }
        // TODO: Change the language of subtype name according to subtype's locale.
        return mPackageManager.getText(
                imi.getPackageName(), subtype.getNameResId(), imi.getServiceInfo().applicationInfo);
    }

    private Drawable getSubtypeIcon(InputMethodInfo imi, InputMethodSubtype subtype) {
        if (imi != null) {
            if (DEBUG) {
                Log.d(TAG, "Update icons of IME: " + imi.getPackageName());
                if (subtype != null) {
                    Log.d(TAG, "subtype =" + subtype.getLocale() + "," + subtype.getMode());
                }
            }
            if (subtype != null) {
                return mPackageManager.getDrawable(imi.getPackageName(), subtype.getIconResId(),
                        imi.getServiceInfo().applicationInfo);
            } else if (imi.getSubtypeCount() > 0) {
                return mPackageManager.getDrawable(imi.getPackageName(),
                        imi.getSubtypeAt(0).getIconResId(),
                        imi.getServiceInfo().applicationInfo);
            } else {
                try {
                    return mPackageManager.getApplicationInfo(
                            imi.getPackageName(), 0).loadIcon(mPackageManager);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "IME can't be found: " + imi.getPackageName());
                }
            }
        }
        return null;
    }
}
