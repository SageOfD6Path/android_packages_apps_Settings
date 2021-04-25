/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.core.InstrumentedPreferenceFragment;
import com.android.settings.testutils.FakeFeatureFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
public final class BatteryChartPreferenceControllerTest {
    private static final String PREF_KEY = "pref_key";
    private static final String PREF_SUMMARY = "fake preference summary";

    @Mock private InstrumentedPreferenceFragment mFragment;
    @Mock private SettingsActivity mSettingsActivity;
    @Mock private PreferenceGroup mAppListGroup;
    @Mock private PackageManager mPackageManager;
    @Mock private Drawable mDrawable;
    @Mock private BatteryHistEntry mBatteryHistEntry;
    @Mock private BatteryChartView mBatteryChartView;
    @Mock private PowerGaugePreference mPowerGaugePreference;
    @Mock private ExpandDividerPreference mExpandDividerPreference;
    @Mock private BatteryUtils mBatteryUtils;

    private Context mContext;
    private BatteryDiffEntry mBatteryDiffEntry;
    private BatteryChartPreferenceController mBatteryChartPreferenceController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        mBatteryChartPreferenceController =
            new BatteryChartPreferenceController(
                mContext, "app_list", /*lifecycle=*/ null,
                mSettingsActivity, mFragment);
        mBatteryChartPreferenceController.mPrefContext = mContext;
        mBatteryChartPreferenceController.mAppListPrefGroup = mAppListGroup;
        mBatteryChartPreferenceController.mBatteryChartView = mBatteryChartView;
        mBatteryDiffEntry = new BatteryDiffEntry(
            mContext,
            /*foregroundUsageTimeInMs=*/ 1,
            /*backgroundUsageTimeInMs=*/ 2,
            /*consumePower=*/ 3,
            mBatteryHistEntry);
        mBatteryDiffEntry = spy(mBatteryDiffEntry);
        // Adds fake testing data.
        BatteryDiffEntry.sResourceCache.put(
            "fakeBatteryDiffEntryKey",
            new BatteryEntry.NameAndIcon("fakeName", /*icon=*/ null, /*iconId=*/ 1));
        mBatteryChartPreferenceController.setBatteryHistoryMap(
            createBatteryHistoryMap(/*size=*/ 5));
    }

    @Test
    public void testOnDestroy_activityIsChanging_clearBatteryEntryCache() {
        doReturn(true).when(mSettingsActivity).isChangingConfigurations();
        // Ensures the testing environment is correct.
        assertThat(BatteryDiffEntry.sResourceCache).hasSize(1);

        mBatteryChartPreferenceController.onDestroy();
        assertThat(BatteryDiffEntry.sResourceCache).isEmpty();
    }

    @Test
    public void testOnDestroy_activityIsNotChanging_notClearBatteryEntryCache() {
        doReturn(false).when(mSettingsActivity).isChangingConfigurations();
        // Ensures the testing environment is correct.
        assertThat(BatteryDiffEntry.sResourceCache).hasSize(1);

        mBatteryChartPreferenceController.onDestroy();
        assertThat(BatteryDiffEntry.sResourceCache).isNotEmpty();
    }

    @Test
    public void testOnDestroy_clearPreferenceCache() {
        // Ensures the testing environment is correct.
        mBatteryChartPreferenceController.mPreferenceCache.put(
            PREF_KEY, mPowerGaugePreference);
        assertThat(mBatteryChartPreferenceController.mPreferenceCache).hasSize(1);

        mBatteryChartPreferenceController.onDestroy();
        // Verifies the result after onDestroy.
        assertThat(mBatteryChartPreferenceController.mPreferenceCache).isEmpty();
    }

    @Test
    public void testOnDestroy_removeAllPreferenceFromPreferenceGroup() {
        mBatteryChartPreferenceController.onDestroy();
        verify(mAppListGroup).removeAll();
    }

    @Test
    public void testSetBatteryHistoryMap_createExpectedKeysAndLevels() {
        mBatteryChartPreferenceController.setBatteryHistoryMap(
            createBatteryHistoryMap(/*size=*/ 5));

        // Verifies the created battery keys array.
        for (int index = 0; index < 25; index++) {
            assertThat(mBatteryChartPreferenceController.mBatteryHistoryKeys[index])
                // These values is are calculated by hand from createBatteryHistoryMap().
                .isEqualTo(index < 20 ? 0 : (index - 20 + 1));
        }
        // Verifies the created battery levels array.
        for (int index = 0; index < 13; index++) {
            assertThat(mBatteryChartPreferenceController.mBatteryHistoryLevels[index])
                // These values is are calculated by hand from createBatteryHistoryMap().
                .isEqualTo(index < 10 ? 0 : (100 - (index - 10) * 2));
        }
        assertThat(mBatteryChartPreferenceController.mBatteryIndexedMap).hasSize(13);
    }

    @Test
    public void testSetBatteryHistoryMap_largeSize_createExpectedKeysAndLevels() {
        mBatteryChartPreferenceController.setBatteryHistoryMap(
            createBatteryHistoryMap(/*size=*/ 25));

        // Verifies the created battery keys array.
        for (int index = 0; index < 25; index++) {
          assertThat(mBatteryChartPreferenceController.mBatteryHistoryKeys[index])
              // These values is are calculated by hand from createBatteryHistoryMap().
              .isEqualTo(index + 1);
        }
        // Verifies the created battery levels array.
        for (int index = 0; index < 13; index++) {
          assertThat(mBatteryChartPreferenceController.mBatteryHistoryLevels[index])
              // These values is are calculated by hand from createBatteryHistoryMap().
              .isEqualTo(100 - index * 2);
        }
        assertThat(mBatteryChartPreferenceController.mBatteryIndexedMap).hasSize(13);
    }

    @Test
    public void testRefreshUi_batteryIndexedMapIsNull_ignoreRefresh() {
        mBatteryChartPreferenceController.setBatteryHistoryMap(null);
        assertThat(mBatteryChartPreferenceController.refreshUi(
            /*trapezoidIndex=*/ 1, /*isForce=*/ false)).isFalse();
    }

    @Test
    public void testRefreshUi_batteryChartViewIsNull_ignoreRefresh() {
        mBatteryChartPreferenceController.mBatteryChartView = null;
        assertThat(mBatteryChartPreferenceController.refreshUi(
            /*trapezoidIndex=*/ 1, /*isForce=*/ false)).isFalse();
    }

    @Test
    public void testRefreshUi_trapezoidIndexIsNotChanged_ignoreRefresh() {
        final int trapezoidIndex = 1;
        mBatteryChartPreferenceController.mTrapezoidIndex = trapezoidIndex;
        assertThat(mBatteryChartPreferenceController.refreshUi(
            trapezoidIndex, /*isForce=*/ false)).isFalse();
    }

    @Test
    public void testRefreshUi_forceUpdate_refreshUi() {
        final int trapezoidIndex = 1;
        mBatteryChartPreferenceController.mTrapezoidIndex = trapezoidIndex;
        assertThat(mBatteryChartPreferenceController.refreshUi(
            trapezoidIndex, /*isForce=*/ true)).isTrue();
    }

    @Test
    public void testForceRefreshUi_updateTrapezoidIndexIntoSelectAll() {
        mBatteryChartPreferenceController.mTrapezoidIndex =
            BatteryChartView.SELECTED_INDEX_INVALID;
        mBatteryChartPreferenceController.setBatteryHistoryMap(
            createBatteryHistoryMap(/*size=*/ 25));

        assertThat(mBatteryChartPreferenceController.mTrapezoidIndex)
            .isEqualTo(BatteryChartView.SELECTED_INDEX_ALL);
    }

    @Test
    public void testRemoveAndCacheAllPrefs_emptyContent_ignoreRemoveAll() {
        final int trapezoidIndex = 1;
        doReturn(0).when(mAppListGroup).getPreferenceCount();

        mBatteryChartPreferenceController.refreshUi(
            trapezoidIndex, /*isForce=*/ true);
        verify(mAppListGroup, never()).removeAll();
    }

    @Test
    public void testRemoveAndCacheAllPrefs_buildCacheAndRemoveAllPreference() {
        final int trapezoidIndex = 1;
        doReturn(1).when(mAppListGroup).getPreferenceCount();
        doReturn(mPowerGaugePreference).when(mAppListGroup).getPreference(0);
        doReturn(PREF_KEY).when(mPowerGaugePreference).getKey();
        // Ensures the testing data is correct.
        assertThat(mBatteryChartPreferenceController.mPreferenceCache).isEmpty();

        mBatteryChartPreferenceController.refreshUi(
            trapezoidIndex, /*isForce=*/ true);

        assertThat(mBatteryChartPreferenceController.mPreferenceCache.get(PREF_KEY))
            .isEqualTo(mPowerGaugePreference);
        verify(mAppListGroup).removeAll();
    }

    @Test
    public void testAddPreferenceToScreen_emptyContent_ignoreAddPreference() {
        mBatteryChartPreferenceController.addPreferenceToScreen(
            new ArrayList<BatteryDiffEntry>());
        verify(mAppListGroup, never()).addPreference(any());
    }

    @Test
    public void testAddPreferenceToScreen_addPreferenceIntoScreen() {
        final String appLabel = "fake app label";
        doReturn(1).when(mAppListGroup).getPreferenceCount();
        doReturn(mDrawable).when(mBatteryDiffEntry).getAppIcon();
        doReturn(appLabel).when(mBatteryDiffEntry).getAppLabel();
        doReturn(PREF_KEY).when(mBatteryHistEntry).getKey();
        doReturn(null).when(mAppListGroup).findPreference(PREF_KEY);

        mBatteryChartPreferenceController.addPreferenceToScreen(
            Arrays.asList(mBatteryDiffEntry));

        // Verifies the preference cache.
        final PowerGaugePreference pref =
            (PowerGaugePreference) mBatteryChartPreferenceController.mPreferenceCache
                .get(PREF_KEY);
        assertThat(pref).isNotNull();
        // Verifies the added preference configuration.
        verify(mAppListGroup).addPreference(pref);
        assertThat(pref.getKey()).isEqualTo(PREF_KEY);
        assertThat(pref.getTitle()).isEqualTo(appLabel);
        assertThat(pref.getIcon()).isEqualTo(mDrawable);
        assertThat(pref.getOrder()).isEqualTo(1);
        assertThat(pref.getBatteryDiffEntry()).isSameInstanceAs(mBatteryDiffEntry);
        assertThat(pref.isSingleLineTitle()).isTrue();
    }

    @Test
    public void testAddPreferenceToScreen_alreadyInScreen_notAddPreferenceAgain() {
        final String appLabel = "fake app label";
        doReturn(1).when(mAppListGroup).getPreferenceCount();
        doReturn(mDrawable).when(mBatteryDiffEntry).getAppIcon();
        doReturn(appLabel).when(mBatteryDiffEntry).getAppLabel();
        doReturn(PREF_KEY).when(mBatteryHistEntry).getKey();
        doReturn(mPowerGaugePreference).when(mAppListGroup).findPreference(PREF_KEY);

        mBatteryChartPreferenceController.addPreferenceToScreen(
            Arrays.asList(mBatteryDiffEntry));

        verify(mAppListGroup, never()).addPreference(any());
    }

    @Test
    public void testHandlePreferenceTreeClick_notPowerGaugePreference_returnFalse() {
        assertThat(mBatteryChartPreferenceController.handlePreferenceTreeClick(mAppListGroup))
            .isFalse();
    }

    @Test
    public void testHandlePreferenceTreeClick_validPackageName_returnTrue() {
        doReturn(false).when(mBatteryHistEntry).isAppEntry();
        doReturn(mBatteryDiffEntry).when(mPowerGaugePreference).getBatteryDiffEntry();

        assertThat(mBatteryChartPreferenceController.handlePreferenceTreeClick(
            mPowerGaugePreference)).isTrue();
    }

    @Test
    public void testHandlePreferenceTreeClick_appEntryWithInvalidPackage_returnFalse() {
        mBatteryChartPreferenceController.mBatteryUtils = mBatteryUtils;
        doReturn(true).when(mBatteryHistEntry).isAppEntry();
        doReturn(BatteryUtils.UID_NULL).when(mBatteryUtils)
            .getPackageUid(mBatteryHistEntry.mPackageName);
        doReturn(mBatteryDiffEntry).when(mPowerGaugePreference).getBatteryDiffEntry();

        assertThat(mBatteryChartPreferenceController.handlePreferenceTreeClick(
            mPowerGaugePreference)).isFalse();
    }

    @Test
    public void testSetPreferenceSummary_setNullContentIfTotalUsageTimeIsZero() {
        final PowerGaugePreference pref = new PowerGaugePreference(mContext);
        pref.setSummary(PREF_SUMMARY);

        mBatteryChartPreferenceController.setPreferenceSummary(
            pref, createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ 0,
                /*backgroundUsageTimeInMs=*/ 0));
        assertThat(pref.getSummary()).isNull();
    }

    @Test
    public void testSetPreferenceSummary_setBackgroundUsageTimeOnly() {
        final PowerGaugePreference pref = new PowerGaugePreference(mContext);
        pref.setSummary(PREF_SUMMARY);

        mBatteryChartPreferenceController.setPreferenceSummary(
            pref, createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ 0,
                /*backgroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS));
        assertThat(pref.getSummary()).isEqualTo("Background: 1 min");
    }

    @Test
    public void testSetPreferenceSummary_setTotalUsageTimeLessThanAMinute() {
        final PowerGaugePreference pref = new PowerGaugePreference(mContext);
        pref.setSummary(PREF_SUMMARY);

        mBatteryChartPreferenceController.setPreferenceSummary(
            pref, createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ 100,
                /*backgroundUsageTimeInMs=*/ 200));
        assertThat(pref.getSummary()).isEqualTo("Total: less than a min");
    }

    @Test
    public void testSetPreferenceSummary_setTotalTimeIfBackgroundTimeLessThanAMinute() {
        final PowerGaugePreference pref = new PowerGaugePreference(mContext);
        pref.setSummary(PREF_SUMMARY);

        mBatteryChartPreferenceController.setPreferenceSummary(
            pref, createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS,
                /*backgroundUsageTimeInMs=*/ 200));
        assertThat(pref.getSummary()).isEqualTo("Total: 1 min");
    }

    @Test
    public void testSetPreferenceSummary_setTotalAndBackgroundUsageTime() {
        final PowerGaugePreference pref = new PowerGaugePreference(mContext);
        pref.setSummary(PREF_SUMMARY);

        mBatteryChartPreferenceController.setPreferenceSummary(
            pref, createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS,
                /*backgroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS));
        assertThat(pref.getSummary()).isEqualTo("Total: 2 min\nBackground: 1 min");
    }

    @Test
    public void testSetPreferenceSummary_notAllowShownPackage_setSummayAsNull() {
        final PowerGaugePreference pref = new PowerGaugePreference(mContext);
        pref.setSummary(PREF_SUMMARY);
        final BatteryDiffEntry batteryDiffEntry =
            spy(createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS,
                /*backgroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS));
        doReturn("com.google.android.googlequicksearchbox").when(batteryDiffEntry)
            .getPackageName();

        mBatteryChartPreferenceController.setPreferenceSummary(pref, batteryDiffEntry);
        assertThat(pref.getSummary()).isNull();
    }

    @Test
    public void testValidateUsageTime_returnTrueIfBatteryDiffEntryIsValid() {
        assertThat(BatteryChartPreferenceController.validateUsageTime(
            createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS,
                /*backgroundUsageTimeInMs=*/ DateUtils.MINUTE_IN_MILLIS)))
            .isTrue();
    }

    @Test
    public void testValidateUsageTime_foregroundTimeExceedThreshold_returnFalse() {
        assertThat(BatteryChartPreferenceController.validateUsageTime(
            createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ DateUtils.HOUR_IN_MILLIS * 3,
                /*backgroundUsageTimeInMs=*/ 0)))
            .isFalse();
    }

    @Test
    public void testValidateUsageTime_backgroundTimeExceedThreshold_returnFalse() {
        assertThat(BatteryChartPreferenceController.validateUsageTime(
            createBatteryDiffEntry(
                /*foregroundUsageTimeInMs=*/ 0,
                /*backgroundUsageTimeInMs=*/ DateUtils.HOUR_IN_MILLIS * 3)))
            .isFalse();
    }

    @Test
    public void testValidateSlotTimestamp_emptyContent_returnTrue() {
        assertThat(BatteryChartPreferenceController.validateSlotTimestamp(
            new ArrayList<Long>())).isTrue();
    }

    @Test
    public void testValidateSlotTimestamp_returnExpectedResult() {
        final ArrayList<Long> slotTimestampList = new ArrayList<Long>(
            Arrays.asList(
                Long.valueOf(0),
                Long.valueOf(DateUtils.HOUR_IN_MILLIS),
                Long.valueOf(DateUtils.HOUR_IN_MILLIS * 2 + DateUtils.MINUTE_IN_MILLIS),
                Long.valueOf(DateUtils.HOUR_IN_MILLIS * 3 + DateUtils.MINUTE_IN_MILLIS * 2)));
        // Verifies the testing data is correct before we added invalid data into it.
        assertThat(BatteryChartPreferenceController.validateSlotTimestamp(slotTimestampList))
            .isTrue();

        // Insert invalid timestamp into the list.
        slotTimestampList.add(
            Long.valueOf(DateUtils.HOUR_IN_MILLIS * 4 + DateUtils.MINUTE_IN_MILLIS * 6));
        assertThat(BatteryChartPreferenceController.validateSlotTimestamp(slotTimestampList))
            .isFalse();
    }

    @Test
    public void testOnExpand_expandedIsTrue_addSystemEntriesToPreferenceGroup() {
        doReturn(1).when(mAppListGroup).getPreferenceCount();
        mBatteryChartPreferenceController.mSystemEntries.add(mBatteryDiffEntry);
        doReturn("label").when(mBatteryDiffEntry).getAppLabel();
        doReturn(mDrawable).when(mBatteryDiffEntry).getAppIcon();
        doReturn(PREF_KEY).when(mBatteryHistEntry).getKey();

        mBatteryChartPreferenceController.onExpand(/*isExpanded=*/ true);

        final ArgumentCaptor<Preference> captor = ArgumentCaptor.forClass(Preference.class);
        verify(mAppListGroup).addPreference(captor.capture());
        // Verifies the added preference.
        assertThat(captor.getValue().getKey()).isEqualTo(PREF_KEY);
    }

    @Test
    public void testOnExpand_expandedIsFalse_removeSystemEntriesFromPreferenceGroup() {
        doReturn(PREF_KEY).when(mBatteryHistEntry).getKey();
        doReturn(mPowerGaugePreference).when(mAppListGroup).findPreference(PREF_KEY);
        mBatteryChartPreferenceController.mSystemEntries.add(mBatteryDiffEntry);
        // Verifies the cache is empty first.
        assertThat(mBatteryChartPreferenceController.mPreferenceCache).isEmpty();

        mBatteryChartPreferenceController.onExpand(/*isExpanded=*/ false);

        verify(mAppListGroup).findPreference(PREF_KEY);
        verify(mAppListGroup).removePreference(mPowerGaugePreference);
        assertThat(mBatteryChartPreferenceController.mPreferenceCache).hasSize(1);
    }

    @Test
    public void testRefreshCategoryTitle_appComponent_setHourIntoPreferenceTitle() {
        setUpBatteryHistoryKeys();
        mBatteryChartPreferenceController.mCategoryTitleType =
            BatteryChartPreferenceController.CategoryTitleType.TYPE_APP_COMPONENT;
        mBatteryChartPreferenceController.mExpandDividerPreference =
            mExpandDividerPreference;
        // Simulates select the first slot.
        mBatteryChartPreferenceController.mTrapezoidIndex = 0;

        mBatteryChartPreferenceController.refreshCategoryTitle();

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Verifies the title in the preference group.
        verify(mAppListGroup).setTitle(captor.capture());
        assertThat(captor.getValue())
            .isEqualTo("App usage for 4 pm-7 am");
        verify(mExpandDividerPreference).setTitle(null);
    }

    @Test
    public void testRefreshCategoryTitle_systemComponent_setHourIntoPreferenceTitle() {
        setUpBatteryHistoryKeys();
        mBatteryChartPreferenceController.mCategoryTitleType =
            BatteryChartPreferenceController.CategoryTitleType.TYPE_SYSTEM_COMPONENT;
        mBatteryChartPreferenceController.mExpandDividerPreference =
            mExpandDividerPreference;
        // Simulates select the first slot.
        mBatteryChartPreferenceController.mTrapezoidIndex = 0;

        mBatteryChartPreferenceController.refreshCategoryTitle();

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Verifies the title in the preference group.
        verify(mAppListGroup).setTitle(captor.capture());
        assertThat(captor.getValue())
            .isEqualTo("System usage for 4 pm-7 am");
        verify(mExpandDividerPreference).setTitle(null);
    }

    @Test
    public void testRefreshCategoryTitle_allComponents_setHourIntoBothTitleTextView() {
        setUpBatteryHistoryKeys();
        mBatteryChartPreferenceController.mCategoryTitleType =
            BatteryChartPreferenceController.CategoryTitleType.TYPE_ALL_COMPONENTS;
        mBatteryChartPreferenceController.mExpandDividerPreference =
            mExpandDividerPreference;
        // Simulates select the first slot.
        mBatteryChartPreferenceController.mTrapezoidIndex = 0;

        mBatteryChartPreferenceController.refreshCategoryTitle();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Verifies the title in the preference group.
        verify(mAppListGroup).setTitle(captor.capture());
        assertThat(captor.getValue())
            .isEqualTo("App usage for 4 pm-7 am");
        // Verifies the title in the expandable divider.
        captor = ArgumentCaptor.forClass(String.class);
        verify(mExpandDividerPreference).setTitle(captor.capture());
        assertThat(captor.getValue())
            .isEqualTo("System usage for 4 pm-7 am");
    }

    @Test
    public void testRefreshCategoryTitle_allComponents_setLast24HrIntoBothTitleTextView() {
        mBatteryChartPreferenceController.mCategoryTitleType =
            BatteryChartPreferenceController.CategoryTitleType.TYPE_ALL_COMPONENTS;
        mBatteryChartPreferenceController.mExpandDividerPreference =
            mExpandDividerPreference;
        // Simulates select all condition.
        mBatteryChartPreferenceController.mTrapezoidIndex =
            BatteryChartView.SELECTED_INDEX_ALL;

        mBatteryChartPreferenceController.refreshCategoryTitle();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Verifies the title in the preference group.
        verify(mAppListGroup).setTitle(captor.capture());
        assertThat(captor.getValue())
            .isEqualTo("App usage for past 24 hr");
        // Verifies the title in the expandable divider.
        captor = ArgumentCaptor.forClass(String.class);
        verify(mExpandDividerPreference).setTitle(captor.capture());
        assertThat(captor.getValue())
            .isEqualTo("System usage for past 24 hr");
    }

    private static Map<Long, List<BatteryHistEntry>> createBatteryHistoryMap(int size) {
        final Map<Long, List<BatteryHistEntry>> batteryHistoryMap = new HashMap<>();
        for (int index = 0; index < size; index++) {
            final ContentValues values = new ContentValues();
            values.put("batteryLevel", Integer.valueOf(100 - index));
            final BatteryHistEntry entry = new BatteryHistEntry(values);
            batteryHistoryMap.put(Long.valueOf(index + 1), Arrays.asList(entry));
        }
        return batteryHistoryMap;
    }

    private BatteryDiffEntry createBatteryDiffEntry(
            long foregroundUsageTimeInMs, long backgroundUsageTimeInMs) {
        return new BatteryDiffEntry(
            mContext, foregroundUsageTimeInMs, backgroundUsageTimeInMs,
            /*consumePower=*/ 0, mBatteryHistEntry);
    }

    private void setUpBatteryHistoryKeys() {
        mBatteryChartPreferenceController.mBatteryHistoryKeys =
            new long[] {1619196786769L, 0L, 1619247636826L};
        ConvertUtils.utcToLocalTimeHour(/*timestamp=*/ 0);
        // Simulates the locale in GMT.
        ConvertUtils.sSimpleDateFormatForHour
             .setTimeZone(TimeZone.getTimeZone("GMT"));
    }
}
