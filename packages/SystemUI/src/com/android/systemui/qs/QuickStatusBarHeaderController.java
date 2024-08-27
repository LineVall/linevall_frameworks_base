/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.qs;

import static android.provider.Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.view.View;
import android.text.TextUtils;
import android.widget.TextView;

import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.scene.shared.flag.SceneContainerFlags;
import com.android.systemui.util.ViewController;
import com.android.systemui.retail.domain.interactor.RetailModeInteractor;
import com.android.systemui.settings.UserTracker;

import com.android.systemui.util.settings.GlobalSettings;

import com.android.settingslib.wifi.WifiStatusTracker;

import java.util.Arrays;

import javax.inject.Inject;

import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.telephony.SubscriptionManager;

import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.WifiStatusTrackerFactory;
import com.android.systemui.tuner.TunerService;

import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.QuickStatusBarHeader;
import com.android.systemui.qs.QuickQSPanelController;
import com.android.systemui.qs.QSPanelController;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.qs.tiles.dialog.bluetooth.BluetoothTileDialogViewModel;
import com.android.systemui.qs.tiles.dialog.InternetDialogManager;
import com.android.systemui.media.dialog.MediaOutputDialogFactory;

/**
 * Controller for {@link QuickStatusBarHeader}.
 */
@QSScope
class QuickStatusBarHeaderController extends ViewController<QuickStatusBarHeader>
        implements TunerService.Tunable {

    private final QuickQSPanelController mQuickQSPanelController;
    private final FalsingManager mFalsingManager;
    private final ActivityStarter mActivityStarter;
    private final View mEditButton;
    private final TextView mUsageText;
    private final WifiStatusTracker mWifiTracker;
    private final NetworkController mNetworkController;
    private final Context mContext;
    private final TunerService mTunerService;
    private final GlobalSettings mGlobalSettings;
    private final SubscriptionManager mSubManager;
    private boolean mListening;

    private static final String INTERNET_TILE = "internet";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mWifiTracker.handleBroadcast(intent);
            onWifiStatusUpdated();
        }
    };

    private final SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setNoSims(boolean show, boolean simDetected) {
            mView.setNoSims(show);
        }
    };

    private final ContentObserver mDataSwitchObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onDefaultDataSimChanged();
        }
    };

    @Inject
    QuickStatusBarHeaderController(
        QuickStatusBarHeader quickStatusBarHeader,
        QuickQSPanelController quickQSPanelController,
        QSPanelController qsPanelController,
        AccessPointController accessPointController,
        BluetoothController bluetoothController,
        BluetoothTileDialogViewModel bluetoothDialogViewModel,
        InternetDialogManager InternetDialogManager,
        FalsingManager falsingManager,
        ActivityStarter activityStarter,
        MediaOutputDialogFactory mediaOutputDialogFactory,
        NetworkController networkController,
        WifiStatusTrackerFactory trackerFactory,
        Context context,
        TunerService tunerService,
        GlobalSettings globalSettings
    ) {
        super(quickStatusBarHeader);
        mQuickQSPanelController = quickQSPanelController;
        mFalsingManager = falsingManager;
        mActivityStarter = activityStarter;
        mNetworkController = networkController;
        mContext = context;
        mTunerService = tunerService;
        mGlobalSettings = globalSettings;
        mSubManager = context.getSystemService(SubscriptionManager.class);
        mWifiTracker = trackerFactory.createTracker(this::onWifiStatusUpdated, null);
        quickStatusBarHeader.mQSPanelController = qsPanelController;
        quickStatusBarHeader.mAccessPointController = accessPointController;
        quickStatusBarHeader.mBluetoothDialogViewModel = bluetoothDialogViewModel;
        quickStatusBarHeader.mInternetDialogManager = InternetDialogManager;
        quickStatusBarHeader.mBluetoothController = bluetoothController;
        quickStatusBarHeader.mMediaOutputDialogFactory = mediaOutputDialogFactory;

        mEditButton = mView.findViewById(android.R.id.edit);
        mUsageText = mView.findViewById(R.id.data_usage);
    }

    @Override
    protected void onViewAttached() {
        // ignore
        mUsageText.setOnClickListener(view -> {
            Intent nIntent = new Intent(Intent.ACTION_MAIN);
            nIntent.setClassName("com.android.settings",
                    "com.android.settings.Settings$DataUsageSummaryActivity");
            mActivityStarter.startActivity(nIntent, true /* dismissShade */);
        });

        final IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
        mWifiTracker.fetchInitialState();
        mWifiTracker.setListening(true);
        mNetworkController.addCallback(mSignalCallback);
        mGlobalSettings.registerContentObserver(MULTI_SIM_DATA_CALL_SUBSCRIPTION,
                mDataSwitchObserver);

        // set initial values
        onWifiStatusUpdated();
        onDefaultDataSimChanged();
    }

    @Override
    protected void onViewDetached() {
        mContext.unregisterReceiver(mReceiver);
        mNetworkController.removeCallback(mSignalCallback);
        mTunerService.removeTunable(this);
        mGlobalSettings.unregisterContentObserver(mDataSwitchObserver);
        setListening(false);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        //Ignore
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;

        mQuickQSPanelController.setListening(listening);

        if (mQuickQSPanelController.switchTileLayout(false)) {
            mView.updateResources();
        }
    }

    public void setContentMargins(int marginStart, int marginEnd) {
        mQuickQSPanelController.setContentMargins(marginStart, marginEnd);
    }

    private void onWifiStatusUpdated() {
        mView.setIsWifiConnected(mWifiTracker.connected);
        mView.setWifiSsid(mWifiTracker.ssid);
    }

    private void onDefaultDataSimChanged() {
        int subId = mSubManager.getDefaultDataSubscriptionId();
        mView.setCurrentDataSubId(subId);
    }
}
