/*
 * Copyright (C) 2016 CarbonROM
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

package mx.mdroid.magicalworld.fragments.style;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import static android.content.Context.ACTIVITY_SERVICE;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v7.preference.DropDownPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.ListPreference;
import android.provider.Settings;
import android.app.WallpaperManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.internal.app.MDroidController;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.Utils;

import com.android.settingslib.drawer.SettingsDrawerActivity;

import mx.mdroid.magicalworld.fragments.style.models.Accent;
import mx.mdroid.magicalworld.fragments.style.models.Style;
import mx.mdroid.magicalworld.fragments.style.models.StyleStatus;
import mx.mdroid.magicalworld.fragments.style.util.AccentAdapter;
import mx.mdroid.magicalworld.fragments.style.util.AccentUtils;
import mx.mdroid.magicalworld.fragments.style.util.OverlayManager;
import mx.mdroid.magicalworld.fragments.style.util.UIUtils;

import java.lang.reflect.Method;
import java.text.DateFormat;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import mx.mdroid.magicalworld.extra.MDroidUtils;
import android.graphics.drawable.AdaptiveIconDrawable;

public class StylePreferences extends SettingsPreferenceFragment
        implements MDroidController.Callback {
    private static final String TAG = "Style";
    private static final int INDEX_WALLPAPER = 0;
    private static final int INDEX_TIME = 1;
    private static final int INDEX_LIGHT = 2;
    private static final int INDEX_DARK = 3;
    private static final int INDEX_BLACK = 4;

    private static final String KEY_THEME_AUTO_MODE = "theme_auto_mode";
    private static final String KEY_THEME_START_TIME = "theme_start_time";
    private static final String KEY_THEME_END_TIME = "theme_end_time";

    private static final int DIALOG_START_TIME = 0;
    private static final int DIALOG_END_TIME = 1;

    private static final int INDEX_NOTIFICATION_THEME = 0;
    private static final int INDEX_NOTIFICATION_LIGHT = 1;
    private static final int INDEX_NOTIFICATION_DARK = 2;
    private static final int INDEX_NOTIFICATION_BLACK = 3;
    private static final String NOTIFICATION_STYLE = "notification_style";
    private static final String SWITCH_STYLE = "switch_style";

    private MDroidController mController;
    private DateFormat mTimeFormatter;

    private Preference mStylePref;
    private Preference mAccentPref;
    private ListPreference mNotificationStyle;
    private ListPreference mSwitchStyle;
    private DropDownPreference mAutoModePreference;
    private Preference mStartTimePreference;
    private Preference mEndTimePreference;

    private List<Accent> mAccents;

    private StyleStatus mStyleStatus;

    private byte mOkStatus = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.style);

        ContentResolver resolver = getActivity().getContentResolver();

        final Context context = getContext();
        mController = new MDroidController(context);

        mTimeFormatter = android.text.format.DateFormat.getTimeFormat(context);
        mTimeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        mStylePref = findPreference("theme_global_style");
        mStylePref.setOnPreferenceChangeListener(this::onStyleChange);
        mAutoModePreference = (DropDownPreference) findPreference(KEY_THEME_AUTO_MODE);
        mStartTimePreference = findPreference(KEY_THEME_START_TIME);
        mEndTimePreference = findPreference(KEY_THEME_END_TIME);
        mAutoModePreference.setEntries(new CharSequence[] {
                getString(R.string.theme_auto_mode_custom),
                getString(R.string.theme_auto_mode_twilight)
        });
        mAutoModePreference.setEntryValues(new CharSequence[] {
                String.valueOf(MDroidController.AUTO_MODE_CUSTOM),
                String.valueOf(MDroidController.AUTO_MODE_TWILIGHT)
        });
        mAutoModePreference.setOnPreferenceChangeListener(this::onAutoModePreferenceChange);
        setupStylePref();

        mNotificationStyle = (ListPreference) findPreference(NOTIFICATION_STYLE);
        int notificationStyle = Settings.System.getInt(resolver,
                Settings.System.NOTIFICATION_STYLE, 0);
        int valueIndex = mNotificationStyle.findIndexOfValue(String.valueOf(notificationStyle));
        mNotificationStyle.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mNotificationStyle.setSummary(mNotificationStyle.getEntry());
        mNotificationStyle.setOnPreferenceChangeListener(this::onNotificationStyleChange);
        setupNotificatioStylePref();

        mAccents = AccentUtils.getAccents(getContext(), mStyleStatus);
        mAccentPref = findPreference("style_accent");
        mAccentPref.setOnPreferenceClickListener(this::onAccentClick);
        setupAccentPref();

        Preference automagic = findPreference("style_automagic");
        automagic.setOnPreferenceClickListener(p -> onAutomagicClick());

        Preference restart = findPreference("restart_systemui");
        restart.setOnPreferenceClickListener(p -> restartUi());

        mSwitchStyle = (ListPreference) findPreference(SWITCH_STYLE);
        int switchStyle = Settings.System.getInt(resolver,
                Settings.System.SWITCH_STYLE, 0);
        int switchStyleValueIndex = mSwitchStyle.findIndexOfValue(String.valueOf(switchStyle));
        mSwitchStyle.setValueIndex(switchStyleValueIndex >= 0 ? switchStyleValueIndex : 0);
        mSwitchStyle.setSummary(mSwitchStyle.getEntry());
        mSwitchStyle.setOnPreferenceChangeListener(this::onSwitchStyleChange);

        findPreference(AdaptiveIconDrawable.MASK_SETTING_PROP).setOnPreferenceChangeListener(this::onAdaptiveIconChange);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Listen for changes only while visible.
        mController.setListener(this);

        // Update the current state since it have changed while not visible.
        onAutoModeChanged(mController.getAutoMode());
        onCustomStartTimeChanged(mController.getCustomStartTime());
        onCustomEndTimeChanged(mController.getCustomEndTime());
    }

    @Override
    public void onStop() {
        super.onStop();

        // Stop listening for state changes.
        mController.setListener(null);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mStartTimePreference) {
            showDialog(DIALOG_START_TIME);
            return true;
        } else if (preference == mEndTimePreference) {
            showDialog(DIALOG_END_TIME);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public Dialog onCreateDialog(final int dialogId) {
        if (dialogId == DIALOG_START_TIME || dialogId == DIALOG_END_TIME) {
            final LocalTime initialTime;
            if (dialogId == DIALOG_START_TIME) {
                initialTime = mController.getCustomStartTime();
            } else {
                initialTime = mController.getCustomEndTime();
            }

            final Context context = getContext();
            final boolean use24HourFormat = android.text.format.DateFormat.is24HourFormat(context);
            return new TimePickerDialog(context, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                    final LocalTime time = LocalTime.of(hourOfDay, minute);
                    if (dialogId == DIALOG_START_TIME) {
                        mController.setCustomStartTime(time);
                    } else {
                        mController.setCustomEndTime(time);
                    }
                }
            }, initialTime.getHour(), initialTime.getMinute(), use24HourFormat);
        }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        switch (dialogId) {
            case DIALOG_START_TIME:
                return MetricsEvent.DIALOG_THEME_SET_START_TIME;
            case DIALOG_END_TIME:
                return MetricsEvent.DIALOG_THEME_SET_END_TIME;
            default:
                return 0;
        }
    }

    @Override
    public void onAutoModeChanged(int autoMode) {
        mAutoModePreference.setValue(String.valueOf(autoMode));

        int style = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.THEME_GLOBAL_STYLE, INDEX_WALLPAPER);
        final boolean showCustomSchedule = autoMode == MDroidController.AUTO_MODE_CUSTOM;
        mStartTimePreference.setVisible(showCustomSchedule && style == INDEX_TIME);
        mEndTimePreference.setVisible(showCustomSchedule && style == INDEX_TIME);
    }

    @Override
    public void onCustomStartTimeChanged(LocalTime startTime) {
        mStartTimePreference.setSummary(getFormattedTimeString(startTime));
    }

    @Override
    public void onCustomEndTimeChanged(LocalTime endTime) {
        mEndTimePreference.setSummary(getFormattedTimeString(endTime));
    }

    private String getFormattedTimeString(LocalTime localTime) {
        final Calendar c = Calendar.getInstance();
        c.setTimeZone(mTimeFormatter.getTimeZone());
        c.set(Calendar.HOUR_OF_DAY, localTime.getHour());
        c.set(Calendar.MINUTE, localTime.getMinute());
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return mTimeFormatter.format(c.getTime());
    }

    private boolean onAccentClick(Preference preference) {
        mAccents = AccentUtils.getAccents(getContext(), mStyleStatus);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_accent_title)
                .setAdapter(new AccentAdapter(mAccents, getContext()),
                        (dialog, i) -> onAccentSelected(mAccents.get(i)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        return true;
    }

    private void setupAccentPref() {
        String currentAccent = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.THEME_CURRENT_ACCENT);
        try {
            updateAccentPref(AccentUtils.getAccent(getContext(), currentAccent));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, currentAccent + ": package not found.");
        }
    }

    private void onAccentSelected(Accent accent) {
        String previousAccent = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.THEME_CURRENT_ACCENT);

        OverlayManager om = new OverlayManager(getContext());
        if (!TextUtils.isEmpty(previousAccent)) {
            // Disable previous theme
            om.setEnabled(previousAccent, false);
        }

        Settings.System.putString(getContext().getContentResolver(),
                Settings.System.THEME_CURRENT_ACCENT, accent.getPackageName());

        if (!TextUtils.isEmpty(accent.getPackageName())) {
            // Enable new theme
            om.setEnabled(accent.getPackageName(), true);
        }
        updateAccentPref(accent);
    }

    private void updateAccentPref(Accent accent) {
        int size = getResources().getDimensionPixelSize(R.dimen.style_accent_icon);

        mAccentPref.setSummary(accent.getName());
        mAccentPref.setIcon(UIUtils.getAccentBitmap(getResources(), size, accent.getColor()));
    }

    private boolean onAutomagicClick() {
        Bitmap bitmap = getWallpaperBitmap();
        if (bitmap == null) {
            return false;
        }

        Accent[] accentsArray = new Accent[mAccents.size()];
        mAccents.toArray(accentsArray);

        Palette palette = Palette.from(bitmap).generate();
        new AutomagicTask(palette, this::onAutomagicCompleted).execute(accentsArray);

        return true;
    }

    private void onAutomagicCompleted(Style style) {
        String styleType = getString(style.isLight() ?
                R.string.style_global_entry_light : R.string.style_global_entry_dark).toLowerCase();
        String accentName = style.getAccent().getName().toLowerCase();
        String message = getString(R.string.style_automagic_dialog_content, styleType, accentName);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_automagic_title)
                .setMessage(message)
                .setPositiveButton(R.string.style_automagic_dialog_positive,
                        (dialog, i) -> applyStyle(style))
                .setNegativeButton(android.R.string.cancel,
                        (dialog, i) -> increaseOkStatus())
                .show();
    }

    private void setupStylePref() {
        int preference = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.THEME_GLOBAL_STYLE, INDEX_WALLPAPER);

        setStyleIcon(preference);
        switch (preference) {
            case INDEX_LIGHT:
                mStyleStatus = StyleStatus.LIGHT_ONLY;
                break;
            case INDEX_DARK:
                mStyleStatus = StyleStatus.DARK_ONLY;
                break;
            case INDEX_BLACK:
                mStyleStatus = StyleStatus.BLACK_ONLY;
                break;
            default:
                mStyleStatus = StyleStatus.DYNAMIC;
                break;
        }

        if (mAutoModePreference != null) {
            mAutoModePreference.setVisible(preference == INDEX_TIME ? true : false);
        }
    }

    private void setupNotificatioStylePref() {
        int preference = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.NOTIFICATION_STYLE, 0);

        setNotificationStyleIcon(preference);
    }

    private void applyStyle(Style style) {
        int valueStyle = style.isLight() ? INDEX_LIGHT : INDEX_DARK;
        int valueNotificationStyle = style.isLight() ? INDEX_NOTIFICATION_LIGHT : INDEX_NOTIFICATION_DARK;

        onStyleChange(mStylePref, valueStyle);
        onAccentSelected(style.getAccent());
        onNotificationStyleChange(mNotificationStyle, valueNotificationStyle);
    }

    private boolean onStyleChange(Preference preference, Object newValue) {
        Integer value;
        if (newValue instanceof String) {
            value = Integer.valueOf((String) newValue);
        } else if (newValue instanceof Integer) {
            value = (Integer) newValue;
        } else {
            return false;
        }

        boolean accentCompatibility = checkAccentCompatibility(value);
        if (!accentCompatibility) {
            new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_global_title)
                .setMessage(R.string.style_accent_configuration_not_supported)
                .setPositiveButton(R.string.style_accent_configuration_positive,
                        (dialog, i) -> onAccentConflict(value))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return false;
        }

        int oldValue = Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.THEME_GLOBAL_STYLE, INDEX_WALLPAPER);

        if (oldValue != value){
            try {
                reload();
            }catch (Exception ignored){
            }
        }

        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.THEME_GLOBAL_STYLE, value);

        setupStylePref();
        return true;
    }

    private boolean onNotificationStyleChange(Preference preference, Object newValue) {
        Integer value;
        if (newValue instanceof String) {
            value = Integer.valueOf((String) newValue);
        } else if (newValue instanceof Integer) {
            value = (Integer) newValue;
        } else {
            return false;
        }

        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.NOTIFICATION_STYLE, value);

        int notificationStyle = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.NOTIFICATION_STYLE, 0);
        int valueIndex = mNotificationStyle.findIndexOfValue(String.valueOf(notificationStyle));
        mNotificationStyle.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mNotificationStyle.setSummary(mNotificationStyle.getEntry());

        setupNotificatioStylePref();
        return true;
    }

    private boolean onSwitchStyleChange(Preference preference, Object newValue) {
        Integer value;
        if (newValue instanceof String) {
            value = Integer.valueOf((String) newValue);
        } else if (newValue instanceof Integer) {
            value = (Integer) newValue;
        } else {
            return false;
        }

        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.SWITCH_STYLE, value);

        int switchStyle = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SWITCH_STYLE, 0);
        int valueIndex = mSwitchStyle.findIndexOfValue(String.valueOf(switchStyle));
        mSwitchStyle.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mSwitchStyle.setSummary(mSwitchStyle.getEntry());

        return true;
    }

    private boolean onAdaptiveIconChange(Preference preference, Object newValue) {
        Integer value;
        if (newValue instanceof String) {
            value = Integer.valueOf((String) newValue);
        } else if (newValue instanceof Integer) {
            value = (Integer) newValue;
        } else {
            return false;
        }

        MDroidUtils.showRebootDialog(getContext(), getString(R.string.icon_shape_changed_title),
                getString(R.string.icon_shape_changed_message), true);

        return true;
    }

    private boolean onAutoModePreferenceChange(Preference preference, Object newValue) {
        return mController.setAutoMode(Integer.parseInt((String) newValue));
    }

    private void reload(){
        Intent intent2 = new Intent(Intent.ACTION_MAIN);
        intent2.addCategory(Intent.CATEGORY_HOME);
        intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent2);
        Toast.makeText(getContext(), R.string.applying_theme_toast, Toast.LENGTH_SHORT).show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
              @Override
              public void run() {
                  Intent intent = new Intent(Intent.ACTION_MAIN);
                  intent.setClassName("com.android.settings",
                        "com.android.settings.Settings$StylePreferencesActivity");
                  intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                  intent.putExtra(SettingsDrawerActivity.EXTRA_SHOW_MENU, true);
                  getContext().startActivity(intent);
                  finish();
                  Toast.makeText(getContext(), R.string.theme_applied_toast, Toast.LENGTH_SHORT).show();
              }
        }, 3000);
    }

    private void setStyleIcon(int value) {
        int icon;
        switch (value) {
            case INDEX_TIME:
                icon = R.drawable.ic_style_time;
                break;
            case INDEX_LIGHT:
                icon = R.drawable.ic_style_light;
                break;
            case INDEX_DARK:
                icon = R.drawable.ic_style_dark;
                break;
            case INDEX_BLACK:
                icon = R.drawable.ic_style_black;
                break;
            default:
                icon = R.drawable.ic_style_auto;
                break;
        }

        mStylePref.setIcon(icon);
    }

    private void setNotificationStyleIcon(int value) {
        int icon;
        switch (value) {
            case INDEX_NOTIFICATION_LIGHT:
                icon = R.drawable.ic_style_light;
                break;
            case INDEX_NOTIFICATION_DARK:
                icon = R.drawable.ic_style_dark;
                break;
            case INDEX_NOTIFICATION_BLACK:
                icon = R.drawable.ic_style_black;
                break;
            default:
                icon = R.drawable.ic_style_auto;
                break;
        }

        mNotificationStyle.setIcon(icon);
    }

    private boolean checkAccentCompatibility(int value) {
        String currentAccentPkg = Settings.System.getString(
                getContext().getContentResolver(), Settings.System.THEME_CURRENT_ACCENT);
        StyleStatus supportedStatus;
        try {
            supportedStatus = AccentUtils.getAccent(getContext(), currentAccentPkg)
                .getSupportedStatus();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e.getMessage());
            supportedStatus = StyleStatus.DYNAMIC;
        }

        switch (supportedStatus) {
            case LIGHT_ONLY:
                return value == INDEX_LIGHT;
            case DARK_ONLY:
                return value == INDEX_DARK;
            case BLACK_ONLY:
                return value == INDEX_BLACK;
            case DYNAMIC:
            default: // Never happens, but compilation fails without this
                return true;
        }
    }

    private void onAccentConflict(int value) {
        StyleStatus proposedStatus;
        switch (value) {
            case INDEX_LIGHT:
                proposedStatus = StyleStatus.LIGHT_ONLY;
                break;
            case INDEX_DARK:
                proposedStatus = StyleStatus.DARK_ONLY;
                break;
            case INDEX_BLACK:
                proposedStatus = StyleStatus.BLACK_ONLY;
                break;
            default:
                proposedStatus = StyleStatus.DYNAMIC;
                break;
        }

        // Let the user pick the new accent
        List<Accent> accents = AccentUtils.getAccents(getContext(), proposedStatus);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_accent_title)
                .setAdapter(new AccentAdapter(accents, getContext()),
                        (dialog, i) -> {
                            onAccentSelected(accents.get(i));
                            onStyleChange(mStylePref, value);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Nullable
    private Bitmap getWallpaperBitmap() {
        WallpaperManager manager = WallpaperManager.getInstance(getContext());
        Drawable wallpaper = manager.getDrawable();

        if (wallpaper == null) {
            return null;
        }

        if (wallpaper instanceof BitmapDrawable) {
            return ((BitmapDrawable) wallpaper).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(wallpaper.getIntrinsicWidth(),
                wallpaper.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        wallpaper.setBounds(0, 0 , canvas.getWidth(), canvas.getHeight());
        wallpaper.draw(canvas);
        return bitmap;
    }

    private void increaseOkStatus() {
        mOkStatus++;
        if (mOkStatus != 2) {
            return;
        }

        mOkStatus = (byte) 0;
        new AlertDialog.Builder(getActivity())
            .setTitle(android.R.string.ok)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private boolean restartUi() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(ACTIVITY_SERVICE);
            Class ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
            Method getDefault = ActivityManagerNative.getDeclaredMethod("getDefault", null);
            Object amn = getDefault.invoke(null, null);
            Method killApplicationProcess = amn.getClass().getDeclaredMethod
                    ("killApplicationProcess", String.class, int.class);

            getContext().stopService(new Intent().setComponent(new ComponentName("com.android.systemui", "com" +
                    ".android.systemui.SystemUIService")));
            am.killBackgroundProcesses("com.android.systemui");

            for (ActivityManager.RunningAppProcessInfo app : am.getRunningAppProcesses()) {
                if ("com.android.systemui".equals(app.processName)) {
                    killApplicationProcess.invoke(amn, app.processName, app.uid);
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final class AutomagicTask extends AsyncTask<Accent, Void, Style> {
        private static final int COLOR_DEFAULT = Color.BLACK;

        private final Palette mPalette;
        private final Callback mCallback;

        AutomagicTask(Palette palette, Callback callback) {
            mPalette = palette;
            mCallback = callback;
        }

        @NonNull
        @Override
        public Style doInBackground(Accent... accents) {
            int wallpaperColor = mPalette.getVibrantColor(COLOR_DEFAULT);

            // If vibrant color extraction failed, let's try muted color
            if (wallpaperColor == COLOR_DEFAULT) {
                wallpaperColor = mPalette.getMutedColor(COLOR_DEFAULT);
            }

            boolean isLight = UIUtils.isColorLight(wallpaperColor);
            Accent bestAccent = getBestAccent(accents, wallpaperColor, isLight);

            return new Style(bestAccent, isLight);
        }

        @Override
        public void onPostExecute(Style style) {
            mCallback.onDone(style);
        }

        private Accent getBestAccent(Accent[] accents, int wallpaperColor, boolean isLight) {
            int bestIndex = 0;
            double minDiff = Double.MAX_VALUE;
            StyleStatus targetStatus = isLight ? StyleStatus.LIGHT_ONLY : StyleStatus.DARK_ONLY;

            for (int i = 0; i < accents.length; i++) {
                double diff = diff(accents[i].getColor(), wallpaperColor);
                if (diff < minDiff && AccentUtils.isCompatible(targetStatus, accents[i])) {
                    bestIndex = i;
                    minDiff = diff;
                }
            }

            return accents[bestIndex];
        }

        private double diff(@ColorInt int accent, @ColorInt int wallpaper) {
            return Math.sqrt(Math.pow(Color.red(accent) - Color.red(wallpaper), 2) +
                    Math.pow(Color.green(accent) - Color.green(wallpaper), 2) +
                    Math.pow(Color.blue(accent) - Color.blue(wallpaper), 2));
        }
    }

    private interface Callback {
        void onDone(Style style);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.MAGICAL_WORLD;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        return true;
    }

}
