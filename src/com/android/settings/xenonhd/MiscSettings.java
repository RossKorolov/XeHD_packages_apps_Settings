/*
* Copyright (C) 2014 Team Horizon
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.settings.xenonhd;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.IActivityManager;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.settings.R;

public class MiscSettings extends SettingsPreferenceFragment implements
Preference.OnPreferenceChangeListener {
    private static final String TAG = "MiscSettings";

    private static final String SHOW_CLEAR_ALL_RECENTS = "show_clear_all_recents";
    private static final String RECENTS_CLEAR_ALL_LOCATION = "recents_clear_all_location";
    private static final String KEY_TOAST_ANIMATION = "toast_animation";
    private static final String FORCE_EXPANDED_NOTIFICATIONS = "force_expanded_notifications";
    private static final String KEY_LCD_DENSITY = "lcd_density";

    private Context mContext;

    private SwitchPreference mRecentsClearAll;
    private ListPreference mRecentsClearAllLocation;
    private ListPreference mToastAnimation;
    private SwitchPreference mForceExpanded;
    private ListPreference mLcdDensityPreference;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.misc_settings);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        mContext = getActivity().getApplicationContext();

        // Toast Animations
        mToastAnimation = (ListPreference) findPreference(KEY_TOAST_ANIMATION);
        mToastAnimation.setSummary(mToastAnimation.getEntry());
        int CurrentToastAnimation = Settings.System.getInt(getContentResolver(), Settings.System.TOAST_ANIMATION, 1);
        mToastAnimation.setValueIndex(CurrentToastAnimation); //set to index of default value
        mToastAnimation.setSummary(mToastAnimation.getEntries()[CurrentToastAnimation]);
        mToastAnimation.setOnPreferenceChangeListener(this);

        mRecentsClearAll = (SwitchPreference) prefSet.findPreference(SHOW_CLEAR_ALL_RECENTS);
        mRecentsClearAll.setChecked(Settings.System.getIntForUser(resolver,
        Settings.System.SHOW_CLEAR_ALL_RECENTS, 1, UserHandle.USER_CURRENT) == 1);
        mRecentsClearAll.setOnPreferenceChangeListener(this);

        mRecentsClearAllLocation = (ListPreference) prefSet.findPreference(RECENTS_CLEAR_ALL_LOCATION);
        int location = Settings.System.getIntForUser(resolver,
        Settings.System.RECENTS_CLEAR_ALL_LOCATION, 0, UserHandle.USER_CURRENT);
        mRecentsClearAllLocation.setValue(String.valueOf(location));
        mRecentsClearAllLocation.setOnPreferenceChangeListener(this);
        updateRecentsLocation(location);

        mForceExpanded = (SwitchPreference) prefSet.findPreference(FORCE_EXPANDED_NOTIFICATIONS);
        mForceExpanded.setChecked(Settings.System.getIntForUser(resolver,
                Settings.System.FORCE_EXPANDED_NOTIFICATIONS, 0, UserHandle.USER_CURRENT) == 1);
        mForceExpanded.setOnPreferenceChangeListener(this);

        mLcdDensityPreference = (ListPreference) findPreference(KEY_LCD_DENSITY);
        int defaultDensity = DisplayMetrics.DENSITY_DEVICE;
        int currentDensity = DisplayMetrics.DENSITY_CURRENT;
        int currentIndex = -1;
        String[] densityEntries = new String[8];
        for (int idx = 0; idx < 8; ++idx) {
            int pct = (75 + idx*5);
            int val = defaultDensity * pct / 100;
            densityEntries[idx] = Integer.toString(val);
            if (pct == 100) {
                densityEntries[idx] += " (" + getResources().getString(R.string.lcd_density_default) + ")";
            }
            if (currentDensity == val) {
                currentIndex = idx;
            }
        }
        mLcdDensityPreference.setEntries(densityEntries);
        mLcdDensityPreference.setEntryValues(densityEntries);
        if (currentIndex != -1) {
            mLcdDensityPreference.setValueIndex(currentIndex);
        }
        mLcdDensityPreference.setOnPreferenceChangeListener(this);
        updateLcdDensityPreferenceDescription(currentDensity);

    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mRecentsClearAll) {
            boolean show = (Boolean) objValue;
            Settings.System.putIntForUser(getActivity().getContentResolver(),
            Settings.System.SHOW_CLEAR_ALL_RECENTS, show ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mRecentsClearAllLocation) {
            int location = Integer.valueOf((String) objValue);
            Settings.System.putIntForUser(getActivity().getContentResolver(),
            Settings.System.RECENTS_CLEAR_ALL_LOCATION, location, UserHandle.USER_CURRENT);
            updateRecentsLocation(location);
            return true;
        } else if (preference == mToastAnimation) {
            int index = mToastAnimation.findIndexOfValue((String) objValue);
            Settings.System.putString(getContentResolver(), Settings.System.TOAST_ANIMATION, (String) objValue);
            mToastAnimation.setSummary(mToastAnimation.getEntries()[index]);
            Toast.makeText(mContext, "Toast Test", Toast.LENGTH_SHORT).show();
            return true;
        } else if (preference == mForceExpanded) {
            boolean show = (Boolean) objValue;
            Settings.System.putIntForUser(getActivity().getContentResolver(),
            Settings.System.FORCE_EXPANDED_NOTIFICATIONS, show ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mLcdDensityPreference) {
            try {
                // The value must begin with a decimal number.  It may
                // optionally be follewed by a space and arbitrary text.
                String strValue = (String) objValue;
                int idx = strValue.indexOf(' ');
                if (idx > 0) {
                    strValue = strValue.substring(0, idx);
                }
                int value = Integer.parseInt(strValue);
                writeLcdDensityPreference(preference.getContext(), value);
                updateLcdDensityPreferenceDescription(value);
            }
            catch (NumberFormatException e) {
                Log.e(TAG, "could not persist display density setting", e);
            }
        }
        return false;
    }

    private void updateRecentsLocation(int value) {
        ContentResolver resolver = getContentResolver();
        Resources res = getResources();
        int summary = -1;

        Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, value);

        if (value == 0) {
            Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, 0);
            summary = R.string.recents_clear_all_location_top_right;
        } else if (value == 1) {
            Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, 1);
            summary = R.string.recents_clear_all_location_top_left;
        } else if (value == 2) {
            Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, 2);
            summary = R.string.recents_clear_all_location_top_center;
        } else if (value == 3) {
            Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, 3);
            summary = R.string.recents_clear_all_location_bottom_right;
        } else if (value == 4) {
            Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, 4);
            summary = R.string.recents_clear_all_location_bottom_left;
        } else if (value == 5) {
            Settings.System.putInt(resolver, Settings.System.RECENTS_CLEAR_ALL_LOCATION, 5);
            summary = R.string.recents_clear_all_location_bottom_center;
        }
        if (mRecentsClearAllLocation != null && summary != -1) {
            mRecentsClearAllLocation.setSummary(res.getString(summary));
        }
    }

    private void updateLcdDensityPreferenceDescription(int currentDensity) {
        int defaultDensity = DisplayMetrics.DENSITY_DEVICE;
        ListPreference preference = mLcdDensityPreference;
        String summary;
        if (currentDensity < 10 || currentDensity >= 1000) {
            // Unsupported value
            summary = getResources().getString(R.string.lcd_density_unsupported);
        }
        else {
            summary = String.format(getResources().getString(R.string.lcd_density_summary),
                    currentDensity);
            if (currentDensity == defaultDensity) {
                summary += " (" + getResources().getString(R.string.lcd_density_default) + ")";
            }
        }
        preference.setSummary(summary);
    }

    public void writeLcdDensityPreference(final Context context, int value) {
        try {
            SystemProperties.set("persist.sys.lcd_density", Integer.toString(value));
        }
        catch (Exception e) {
            Log.e(TAG, "Unable to save LCD density");
            return;
        }
        final IActivityManager am = ActivityManagerNative.asInterface(ServiceManager.checkService("activity"));
        if (am != null) {
            AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected void onPreExecute() {
                    ProgressDialog dialog = new ProgressDialog(context);
                    dialog.setMessage(getResources().getString(R.string.restarting_ui));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    dialog.show();
                }
                @Override
                protected Void doInBackground(Void... arg0) {
                    // Give the user a second to see the dialog
                    try {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e) {
                        // Ignore
                    }
                    // Restart the UI
                    try {
                        am.restart();
                    }
                    catch (RemoteException e) {
                        Log.e(TAG, "Failed to restart");
                    }
                    return null;
                }
            };
            task.execute((Void[])null);
        }
    }
}
