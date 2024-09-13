/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.android.internal.graphics.ColorUtils;

import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.res.R;
import com.android.systemui.crdroid.header.StatusBarHeaderMachine;
import com.android.systemui.util.LargeScreenUtils;
import android.text.TextUtils;
import android.text.format.Formatter;

import com.bosphere.fadingedgelayout.FadingEdgeLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.Exception;
import java.lang.Math;
import java.util.Iterator;
import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionLegacyHelper;
import android.net.ConnectivityManager;
import com.android.systemui.qs.TouchAnimator;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.qs.tiles.dialog.bluetooth.BluetoothTileDialogViewModel;
import com.android.systemui.utils.palette.Palette;

import com.android.systemui.qs.QSPanelController;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.qs.tiles.dialog.InternetDialogManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.media.dialog.MediaOutputDialogFactory;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout
            implements StatusBarHeaderMachine.IStatusBarHeaderMachineObserver, BluetoothCallback,
            NotificationMediaManager.MediaListener, Palette.PaletteAsyncListener, View.OnClickListener,
            View.OnLongClickListener {

    private static final String TAG = "QuickStatusBarHeader";

    private static final Intent AFTERLAB_SETTING = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.afterlife.afterlab.CustomizeDashboard"));

    private boolean mExpanded;
    private boolean mQsDisabled;
    private boolean mShouldShowUsageText;

    protected QuickQSPanel mHeaderQsPanel;
    public float mKeyguardExpansionFraction;

    // QS Header
    private ImageView mQsHeaderImageView;
    private FadingEdgeLayout mQsHeaderLayout;
    private boolean mHeaderImageEnabled;
    private StatusBarHeaderMachine mStatusBarHeaderMachine;
    private Drawable mCurrentBackground;
    private int mHeaderImageHeight;
    //private final Handler mHandler = new Handler();

    private class OmniSettingsObserver extends ContentObserver {
        OmniSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CUSTOM_HEADER), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT), false,
                    this, UserHandle.USER_ALL);
            }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
    private OmniSettingsObserver mOmniSettingsObserver;

    private int colorActive = Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorAccent);
    private int colorInactive = Utils.getColorAttrDefaultColor(mContext, R.attr.offStateColor);
    private int colorLabelActive = Utils.getColorAttrDefaultColor(mContext, com.android.internal.R.attr.textColorPrimaryInverse);
    private int colorLabelInactive = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
    private int colorSurface = Utils.getColorAttrDefaultColor(mContext, R.attr.colorSurfaceCustom);
    private int textMediaTitle = Utils.getColorAttrDefaultColor(mContext, R.attr.textMediaTitle);
    private int textMediaOplus = Utils.getColorAttrDefaultColor(mContext, R.attr.textMediaOplus);
    private int iconHeaderButtonColor = Utils.getColorAttrDefaultColor(mContext, R.attr.colorHeaderButton);

    private int mColorArtwork = Color.BLACK;
    private int mColorArtworkCard = Color.BLACK;
    private int mMediaTextIconColor = Color.WHITE;

    private ViewGroup mOplusQsContainer;
    private ViewGroup mOplusQsLayout;

    private ViewGroup mOplusQsHeadFooter;

    private ViewGroup mUsageContainer;
    private TextView mUsageText;
    private ImageView mUsageIcon;

    private View mEditButton;
    private ImageView mOplusQsSettingsButton;

    private ViewGroup mBluetoothButton;
    private ImageView mBluetoothIcon;
    private TextView mBluetoothText;
    private ImageView mBluetoothChevron;
    private boolean mBluetoothEnabled;

    private ViewGroup mInternetButton;
    private ImageView mInternetIcon;
    private TextView mInternetText;
    private ImageView mInternetChevron;
    private boolean mInternetEnabled;

    private ImageView mMediaPlayerContainer;
    private ViewGroup mMediaPlayerLayout;
    private ImageView mMediaPlayerAlbum;
    private ImageView mAppIcon, mMediaOutputSwitcher;
    private TextView mMediaPlayerTitle, mMediaPlayerSubtitle;
    private ImageButton mMediaBtnPrev, mMediaBtnNext, mMediaBtnPlayPause;

    private String mMediaTitle, mMediaArtist;
    private Bitmap mMediaArtwork;
    private boolean mMediaIsPlaying;

    private final ActivityStarter mActivityStarter;
    private final ConnectivityManager mConnectivityManager;
    private final SubscriptionManager mSubManager;
    private final WifiManager mWifiManager;
    private final NotificationMediaManager mNotificationMediaManager;

    public TouchAnimator mQQSContainerAnimator, mOplusQsSettingsButtonAnimator, mEditButtonAnimator, mUsageAnimator;

    public FalsingManager mFalsingManager;
    public QSPanelController mQSPanelController;
    public BluetoothController mBluetoothController;
    public BluetoothTileDialogViewModel mBluetoothDialogViewModel;
    public InternetDialogManager mInternetDialogManager;
    public AccessPointController mAccessPointController;
    public MediaOutputDialogFactory mMediaOutputDialogFactory;

    private DataUsageController mDataController;

    private boolean mHasNoSims;
    private boolean mIsWifiConnected;
    private String mWifiSsid;
    private int mSubId;
    private int mCurrentDataSubId;

    private static final long DEBOUNCE_DELAY_MS = 200;

    private final Handler mHandler;
    private Runnable mUpdateRunnableBluetooth;
    private Runnable mUpdateRunnableInternet;
    private Runnable mEditTileBtn;
    private Runnable mSetUsageTextRunnable = this::setUsageText;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStatusBarHeaderMachine = new StatusBarHeaderMachine(context);
        mHandler = new Handler(Looper.getMainLooper());
        mOmniSettingsObserver = new OmniSettingsObserver(mHandler);
        mOmniSettingsObserver.observe();
        mBluetoothEnabled = false;
        mInternetEnabled = false;
        mMediaIsPlaying = false;
        mActivityStarter = (ActivityStarter) Dependency.get(ActivityStarter.class);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mDataController = new DataUsageController(context);
        mSubManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mNotificationMediaManager = (NotificationMediaManager) Dependency.get(NotificationMediaManager.class);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);

        mQsHeaderLayout = findViewById(R.id.layout_header);
        mQsHeaderImageView = findViewById(R.id.qs_header_image_view);
        mQsHeaderImageView.setClipToOutline(true);

        mOplusQsHeadFooter = findViewById(R.id.qs_media_oplus_header_container);

        mUsageContainer = findViewById(R.id.data_usage_container);
        mUsageText = findViewById(R.id.data_usage);
        mUsageIcon = findViewById(R.id.data_usage_icon);

        mEditButton = findViewById(android.R.id.edit);
        mOplusQsSettingsButton = findViewById(R.id.qs_media_oplus_header_settings);

        mOplusQsContainer = findViewById(R.id.qs_container);
        mOplusQsLayout = findViewById(R.id.qs_media_oplus_container);
        mInternetButton = findViewById(R.id.qs_media_oplus_internet_button);
        mInternetIcon = findViewById(R.id.qs_media_oplus_internet_icon);
        mInternetText = findViewById(R.id.qs_media_oplus_internet_text);
        mInternetChevron = findViewById(R.id.qs_media_oplus_internet_chevron);
        mBluetoothButton = findViewById(R.id.qs_media_oplus_bluetooth_button);
        mBluetoothIcon = findViewById(R.id.qs_media_oplus_bluetooth_icon);
        mBluetoothText = findViewById(R.id.qs_media_oplus_bluetooth_text);
        mBluetoothChevron = findViewById(R.id.qs_media_oplus_bluetooth_chevron);

        mMediaPlayerContainer = findViewById(R.id.qs_media_oplus_player_card);
        mMediaPlayerLayout = findViewById(R.id.qs_media_oplus_player_container);
        mMediaPlayerAlbum = findViewById(R.id.qs_media_oplus_album);
        mAppIcon = findViewById(R.id.qs_media_oplus_icon_app);
        mMediaOutputSwitcher = findViewById(R.id.qs_media_oplus_output_switcher);
        mMediaPlayerTitle = findViewById(R.id.qs_media_oplus_player_title);
        mMediaPlayerSubtitle = findViewById(R.id.qs_media_oplus_player_subtitle);
        mMediaBtnPrev = findViewById(R.id.qs_media_oplus_player_action_prev);
        mMediaBtnNext = findViewById(R.id.qs_media_oplus_player_action_next);
        mMediaBtnPlayPause = findViewById(R.id.qs_media_oplus_player_play_pause);

        mNotificationMediaManager.addCallback(this);

        initBluetoothManager();

        mMediaPlayerLayout.setOnClickListener(this);
        mMediaOutputSwitcher.setOnClickListener(this);
        mMediaBtnPrev.setOnClickListener(this);
        mMediaBtnNext.setOnClickListener(this);
        mMediaBtnPlayPause.setOnClickListener(this);

        mOplusQsSettingsButton.setOnClickListener(this);
        mUsageText.setOnClickListener(this::onUsageTextClick);

        mInternetButton.setOnClickListener(this);
        mBluetoothButton.setOnClickListener(this);

        mInternetButton.setOnLongClickListener(this);
        mBluetoothButton.setOnLongClickListener(this);
        mOplusQsSettingsButton.setOnLongClickListener(this);

        updateSettings();

        startUpdateInterntTileStateAsync();
        startUpdateBluetoothTileStateAsync();
        updateSettingsBtn();
        updateEditBtn();
        updateableRunnerEditBtn();
        setUsageText();
    }

    private void onUsageTextClick(View v) {
        if (mSubManager.getActiveSubscriptionInfoCount() > 1) {
            // Get opposite slot 2 ^ 3 = 1, 1 ^ 3 = 2
            mSubId = mSubId ^ 3;
            setUsageText();
            mUsageText.setSelected(false);
            postDelayed(() -> mUsageText.setSelected(true), 1000);
        }
    }

    private void setUsageTextDebounced() {
        mHandler.removeCallbacks(mSetUsageTextRunnable);
        mHandler.postDelayed(mSetUsageTextRunnable, DEBOUNCE_DELAY_MS);
    }

    private void setUsageText() {
        if (mUsageText == null) return;
        Drawable backgroundUsage = mUsageContainer.getBackground();
        backgroundUsage.setTint(colorInactive);
        mUsageIcon.setImageResource(R.drawable.ic_data_usage_icon);
        mUsageIcon.setColorFilter(colorLabelInactive);
        DataUsageController.DataUsageInfo info;
        String suffix;
        if (mIsWifiConnected) {
            info = mDataController.getWifiDailyDataUsageInfo(true);
            if (info == null) {
                info = mDataController.getWifiDailyDataUsageInfo(false);
                suffix = mContext.getResources().getString(R.string.usage_wifi_default_suffix);
            } else {
                suffix = getWifiId();
            }
        } else if (!mHasNoSims) {
            mDataController.setSubscriptionId(mSubId);
            info = mDataController.getDailyDataUsageInfo();
            suffix = getCarrierName();
        } else {
            mShouldShowUsageText = false;
            mUsageText.setText(null);
            updateVisibilities();
            return;
        }
        if (info == null) {
            Log.w(TAG, "setUsageText: DataUsageInfo is NULL.");
            return;
        }
        // Setting text actually triggers a layout pass (because the text view is set to
        // wrap_content width and TextView always relayouts for this). Avoid needless
        // relayout if the text didn't actually change.
        String text = formatDataUsage(info.usageLevel, suffix);
        if (!TextUtils.equals(text, mUsageText.getText())) {
            mUsageText.setText(formatDataUsage(info.usageLevel, suffix));
        }
        mShouldShowUsageText = true;
        updateVisibilities();
        mUsageContainer.setClipToOutline(true);
    }

    private String formatDataUsage(long byteValue, String suffix) {
        // Example: 1.23 GB used today (airtel)
        StringBuilder usage = new StringBuilder(Formatter.formatFileSize(getContext(),
                byteValue, Formatter.FLAG_IEC_UNITS))
                .append(" ")
                .append(mContext.getString(R.string.usage_data));
        return usage.toString();
    }

    private String getCarrierName() {
        SubscriptionInfo subInfo = mSubManager.getActiveSubscriptionInfo(mSubId);
        if (subInfo != null) {
            return subInfo.getDisplayName().toString();
        }
        return mContext.getResources().getString(R.string.usage_data_default_suffix);
    }

    private String getWifiId() {
        if (mWifiSsid != null) {
            return mWifiSsid.replace("\"", "");
        }
        return mContext.getResources().getString(R.string.usage_wifi_default_suffix);
    }

    protected void setWifiSsid(String ssid) {
        if (mWifiSsid != ssid) {
            mWifiSsid = ssid;
            setUsageTextDebounced();
        }
    }

    protected void setIsWifiConnected(boolean connected) {
        if (mIsWifiConnected != connected) {
            mIsWifiConnected = connected;
            setUsageTextDebounced();
        }
    }

    protected void setNoSims(boolean hasNoSims) {
        if (mHasNoSims != hasNoSims) {
            mHasNoSims = hasNoSims;
            setUsageTextDebounced();
        }
    }

    protected void setCurrentDataSubId(int subId) {
        if (mCurrentDataSubId != subId) {
            mSubId = mCurrentDataSubId = subId;
            setUsageTextDebounced();
        }
    }

    private void updateBuildTextResources() {
        FontSizeUtils.updateFontSizeFromStyle(mUsageText, R.style.TextAppearance_QS_Status_DataUsage);
    }

    private void updateUsageAnimator() {
        mUsageAnimator = createUsageAnimator();
    }

    private TouchAnimator createUsageAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mUsageText, "alpha", 0, 1)
                .setStartDelay(0.9f);
        return builder.build();
    }

    private void initBluetoothManager() {
        LocalBluetoothManager localBluetoothManager = LocalBluetoothManager.getInstance(mContext, null);

        if (localBluetoothManager != null) {
            localBluetoothManager.getEventManager().registerCallback(this);
            LocalBluetoothAdapter localBluetoothAdapter = localBluetoothManager.getBluetoothAdapter();
            int bluetoothState = BluetoothAdapter.STATE_DISCONNECTED;

            synchronized (localBluetoothAdapter) {
                if (localBluetoothAdapter.getAdapter().getState() != localBluetoothAdapter.getBluetoothState()) {
                    localBluetoothAdapter.setBluetoothStateInt(localBluetoothAdapter.getAdapter().getState());
                }
                bluetoothState = localBluetoothAdapter.getBluetoothState();
            }
            updateBluetoothState(bluetoothState);
        }
    }

    @Override
    public void onBluetoothStateChanged(@AdapterState int bluetoothState) {
        updateBluetoothState(bluetoothState);
    }

    private void updateBluetoothState(@AdapterState int bluetoothState) {
        mBluetoothEnabled = bluetoothState == BluetoothAdapter.STATE_ON
                || bluetoothState == BluetoothAdapter.STATE_TURNING_ON;
        updateBluetoothTile();
    }

    public final void updateBluetoothTile() {
        if (mBluetoothButton == null
                || mBluetoothIcon == null
                || mBluetoothText == null
                || mBluetoothChevron == null)
            return;
        Drawable background = mBluetoothButton.getBackground();
        if (mBluetoothEnabled) {
            background.setTint(colorActive);
            mBluetoothIcon.setColorFilter(colorLabelActive);
            mBluetoothText.setTextColor(colorLabelActive);
            mBluetoothChevron.setColorFilter(colorLabelActive);
        } else {
            background.setTint(colorInactive);
            mBluetoothIcon.setColorFilter(colorLabelInactive);
            mBluetoothText.setTextColor(colorLabelInactive);
            mBluetoothChevron.setColorFilter(colorLabelInactive);
        }
    }

    private void updateSettingsBtn() {
        Drawable background = mOplusQsSettingsButton.getBackground();
        background.setTint(colorActive);
        mOplusQsSettingsButton.setImageDrawable(getSettingsBtn());
    }

    private Drawable getSettingsBtn() {
        Drawable SettingsBtn = ContextCompat.getDrawable(mContext, R.drawable.ic_media_oplus_settings_btn);
        return SettingsBtn;
    }

    private void updateEditBtn() {
        Drawable background = mEditButton.getBackground();
        background.setTint(colorInactive);
    }

    public void updateOplusQsSettingsButtonAnim() {
        this.mOplusQsSettingsButtonAnimator = new TouchAnimator.Builder()
            .addFloat(this.mOplusQsSettingsButton, "rotation", new float[]{0.0f, 180.0f}).build();
    }

    public void updateEditButtonAnim() {
        /*Resources resourcesZ = getResources();*/
        /*float dimensionPixelEditBtn = (float) resourcesZ.getDimensionPixelSize(R.dimen.oplus_search_expand_translation_x);*/
        /*TouchAnimator.Builder builderQ = new TouchAnimator.Builder();*/
        this.mEditButtonAnimator = new TouchAnimator.Builder()
            .addFloat(this.mEditButton, "alpha", 0, 0, 1).build();
        /*this.mEditButtonAnimator = new TouchAnimator.Builder()
            .addFloat(this.mEditButton, "translationX", new float[]{dimensionPixelEditBtn, 0.0f}).build();*/
        /*this.mEditButtonAnimator = new TouchAnimator.Builder()
            .addFloat(this.mEditButton, "rotation", new float[]{0.0f, 180.0f, 360.0f}).build();*/
        scheduleEditBtnUpdate();
    }

    public void updateInterntTile() {
        if (mInternetButton == null
                || mInternetIcon == null
                || mInternetText == null
                || mInternetChevron == null)
            return;

        String carrier;
        int iconResId = 0;

        if (isWifiConnected()) {
            carrier = getWifiSsid();
            mInternetEnabled = true;
            iconResId = mContext.getResources().getIdentifier("ic_wifi_signal_4", "drawable", "android");
        } else {
            carrier = getSlotCarrierName();
            mInternetEnabled = true;
            iconResId = mContext.getResources().getIdentifier("ic_signal_cellular_4_4_bar", "drawable", "android");
        }

        mInternetText.setText(carrier);
        mInternetIcon.setImageResource(iconResId);

        Drawable background = mInternetButton.getBackground();

        if (isCellularConnected() || isWifiConnected()) {
            background.setTint(colorActive);
            mInternetIcon.setColorFilter(colorLabelActive);
            mInternetText.setTextColor(colorLabelActive);
            mInternetChevron.setColorFilter(colorLabelActive);
        } else {
            background.setTint(colorInactive);
            mInternetIcon.setColorFilter(colorLabelInactive);
            mInternetText.setTextColor(colorLabelInactive);
            mInternetChevron.setColorFilter(colorLabelInactive);
        }
    }

    private boolean isCellularConnected() {
        final Network network = mConnectivityManager.getActiveNetwork();
        if (network != null) {
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } else {
            return false;
        }
    }

    private boolean isWifiConnected() {
        final Network network = mConnectivityManager.getActiveNetwork();
        if (network != null) {
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            return false;
        }
    }

    private String getSlotCarrierName() {
        CharSequence result = mContext.getResources().getString(R.string.quick_settings_internet_label);
        int subId = mSubManager.getDefaultDataSubscriptionId();
        final List<SubscriptionInfo> subInfoList = mSubManager.getActiveSubscriptionInfoList(true);
        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                if (subId == subInfo.getSubscriptionId()) {
                    result = subInfo.getDisplayName();
                    break;
                }
            }
        }
        return result.toString();
    }

    private String getWifiSsid() {
        final WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo.getHiddenSSID() || wifiInfo.getSSID() == WifiManager.UNKNOWN_SSID) {
            return mContext.getResources().getString(R.string.quick_settings_wifi_label);
        } else {
            return wifiInfo.getSSID().replace("\"", "");
        }
    }

    @Override
    public void onPrimaryMetadataOrStateChanged(MediaMetadata metadata, @PlaybackState.State int state) {
        if (metadata != null) {
            CharSequence title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);

            mMediaTitle = title != null ? title.toString() : null;
            mMediaArtist = artist != null ? artist.toString() : null;
            Bitmap albumArtwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            Bitmap mediaArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            mMediaArtwork = (albumArtwork != null) ? albumArtwork : mediaArt;

            if (mMediaArtwork != null) {
                Palette.generateAsync(mMediaArtwork, this);
            }
        } else {
            mMediaTitle = null;
            mMediaArtist = null;
            mMediaArtwork = null;
        }

        if (mMediaArtwork == null) {
            mMediaPlayerTitle.setTextColor(textMediaTitle);
            mMediaPlayerSubtitle.setTextColor(textMediaTitle);
            mMediaBtnPrev.setColorFilter(textMediaTitle);
            mMediaBtnPlayPause.setColorFilter(textMediaTitle);
            mMediaBtnNext.setColorFilter(textMediaTitle);
            mMediaOutputSwitcher.setColorFilter(textMediaTitle);
            mMediaPlayerAlbum.setColorFilter(null);
            mMediaPlayerContainer.setColorFilter(null);
        } else {
            mMediaPlayerTitle.setTextColor(textMediaOplus);
            mMediaPlayerSubtitle.setTextColor(textMediaOplus);
            mMediaBtnPrev.setColorFilter(textMediaTitle);
            mMediaBtnPlayPause.setColorFilter(textMediaTitle);
            mMediaBtnNext.setColorFilter(textMediaTitle);
            mMediaOutputSwitcher.setColorFilter(textMediaOplus);
        }

        mMediaIsPlaying = state == PlaybackState.STATE_PLAYING;

        updateMediaPlayer();
    }

    public void setMediaNotificationColor(int color) {
    }

    @Override
    public void onGenerated(Palette palette) {
        int mShadow = 0;
        int mShadowCard = 130;
        int alphaValue = 0;
        int alphaCard = 100;
        mColorArtwork = ColorUtils.setAlphaComponent(palette.getDarkVibrantColor(Color.BLACK), mShadow);
        mColorArtworkCard = ColorUtils.setAlphaComponent(palette.getDarkVibrantColor(Color.BLACK), mShadowCard);
        int mMediaOutputIconColor = palette.getLightVibrantColor(Color.WHITE);

        mMediaPlayerAlbum.setColorFilter(ColorUtils.setAlphaComponent(mColorArtwork, alphaValue), PorterDuff.Mode.SRC_ATOP);
        mMediaPlayerContainer.setColorFilter(ColorUtils.setAlphaComponent(mColorArtworkCard, alphaCard), PorterDuff.Mode.SRC_ATOP);
        mMediaPlayerContainer.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.MIRROR));

    }

    private void updateMediaPlayer() {
        if (mMediaPlayerContainer == null
                || mMediaPlayerLayout == null
                || mMediaPlayerAlbum == null
                || mAppIcon == null
                || mMediaOutputSwitcher == null
                || mMediaPlayerTitle == null
                || mMediaPlayerSubtitle == null
                || mMediaBtnPrev == null
                || mMediaBtnNext == null
                || mMediaBtnPlayPause == null)
            return;

        mMediaPlayerTitle.setText(mMediaTitle == null ?
                                    mContext.getResources().getString(R.string.qs_media_oplus_default_title) : mMediaTitle);
        mMediaPlayerSubtitle.setText(mMediaArtist == null ?
                                    mContext.getResources().getString(R.string.qs_media_oplus_default_subtitle) : mMediaArtist);

        if (mMediaIsPlaying) {
            mMediaBtnPlayPause.setImageResource(R.drawable.ic_media_oplus_player_act_pause);
        } else {
            mMediaBtnPlayPause.setImageResource(R.drawable.ic_media_oplus_player_act_play);
        }

        if (mNotificationMediaManager != null && mNotificationMediaManager.getMediaIcon() != null
                && mMediaTitle != null) {
            mAppIcon.setImageIcon(mNotificationMediaManager.getMediaIcon());
        } else {
            mAppIcon.setImageResource(R.drawable.ic_media_oplus_player_icon_app);
        }
        mAppIcon.setColorFilter(colorLabelActive);

        Drawable background = mMediaPlayerLayout.getBackground();

        if (mMediaArtwork == null) {
            background.setTint(colorSurface);
        } else {
            background.setTint(colorSurface);
        }

        mMediaPlayerContainer.setImageDrawable(getMediaCardArtwork());
        mMediaPlayerContainer.setClipToOutline(true);
        mMediaPlayerLayout.setClipToOutline(true);
        mMediaPlayerAlbum.setImageDrawable(getMediaArtwork());
        mMediaPlayerAlbum.setClipToOutline(true);
    }

    private Drawable getMediaArtwork() {
        if (mMediaArtwork == null) {
            Drawable artwork = ContextCompat.getDrawable(mContext, R.drawable.ic_media_oplus_player_no_music);
            DrawableCompat.setTint(DrawableCompat.wrap(artwork), colorLabelActive);
            return artwork;
        } else {
            Drawable artwork = new BitmapDrawable(mContext.getResources(), mMediaArtwork);
            return artwork;
        }
    }

    private Drawable getMediaCardArtwork() {
        if (mMediaArtwork == null) {
            Drawable cardArtwork = ContextCompat.getDrawable(mContext, R.drawable.qs_media_oplus_bg_button);
            DrawableCompat.setTint(DrawableCompat.wrap(cardArtwork), colorInactive);
            return cardArtwork;
        } else {
            Drawable cardArtwork = new BitmapDrawable(mContext.getResources(), mMediaArtwork);
            return cardArtwork;
        }
    }

    public void onClick(View v) {
        if (v == mInternetButton) {
            new Handler().post(() -> mInternetDialogManager.create(true,
                    mAccessPointController.canConfigMobileData(),
                    mAccessPointController.canConfigWifi(),
                    v));
        } else if (v == mBluetoothButton) {
            boolean isAutoOn = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.QS_BT_AUTO_ON, 0) == 1;
            mBluetoothDialogViewModel.showDialog(mContext, v, isAutoOn);
        } else if (v == mMediaBtnPrev) {
            mNotificationMediaManager.skipTrackPrevious();
        } else if (v == mMediaBtnPlayPause) {
            mNotificationMediaManager.playPauseTrack();
        } else if (v == mMediaBtnNext) {
            mNotificationMediaManager.skipTrackNext();
        } else if (v == mMediaPlayerLayout) {
            launchMediaPlayer();
        } else if (v == mMediaOutputSwitcher) {
            launchMediaOutputSwitcher(v);
        } else if (v == mOplusQsSettingsButton) {
            launchSettingsBtn();
        } else if (v == mEditButton) {
            boolean isExpanded = mExpanded;
            if (isExpanded) {
                launchEditButton(v);
            };
            return;
        }
    }

    public boolean onLongClick(View v) {
        if (v == mInternetButton) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(Settings.ACTION_WIFI_SETTINGS), 0);
        } else if (v == mBluetoothButton) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 0);
        } else if (v == mOplusQsSettingsButton) {
            launchAfterLabs();
        } else {
            return false;
        }
        return true;
    }

    private void launchMediaPlayer() {
        String packageName = mNotificationMediaManager.getMediaController() != null ? mNotificationMediaManager.getMediaController().getPackageName() : null;
        Intent appIntent = packageName != null ? new Intent(mContext.getPackageManager().getLaunchIntentForPackage(packageName)) : null;
        if (appIntent != null) {
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appIntent.setPackage(packageName);
            mActivityStarter.startActivity(appIntent, true);
            return;
        }

        sendMediaButtonClickEvent();
    }

    private void launchAfterLabs() {
        String settingsPackageName = "com.android.settings";
        new Object().getClass();
        Intent appIntent = new Intent();
        String className = "com.android.settings.Settings$AfterlabSettingsActivity";
        appIntent.setClassName(settingsPackageName, className);
        mActivityStarter.startActivity(appIntent, true);
    }

    private void launchMediaOutputSwitcher(View v) {
        String packageName = mNotificationMediaManager.getMediaController() != null ? mNotificationMediaManager.getMediaController().getPackageName() : null;
        if (packageName != null) {
            mMediaOutputDialogFactory.create(packageName, true, v);
        }
    }

    private void launchSettingsBtn() {
        Intent appIntent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        mActivityStarter.startActivity(appIntent, true);
    }

    private void launchEditButton(View v) {
        mEditButton.setOnClickListener(view ->
            mActivityStarter.postQSRunnableDismissingKeyguard(() -> mQSPanelController.showEdit(view)));
    }

    private void sendMediaButtonClickEvent() {
        long now = SystemClock.uptimeMillis();
        KeyEvent keyEvent = new KeyEvent(now, now, 0, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
        MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        helper.sendMediaButtonEvent(keyEvent, true);
        helper.sendMediaButtonEvent(KeyEvent.changeAction(keyEvent, KeyEvent.ACTION_UP), true);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStatusBarHeaderMachine.addObserver(this);
        mStatusBarHeaderMachine.updateEnablement();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mStatusBarHeaderMachine.removeObserver(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only react to touches inside QuickQSPanel
        if (event.getY() > mHeaderQsPanel.getTop()) {
            return super.onTouchEvent(event);
        } else {
            return false;
        }
    }

    public void upToDown(int scrollY) {
                View mOplusQsContainer = this.mOplusQsContainer;
                mOplusQsContainer.setScrollY(scrollY);
    }

    void updateResources() {
        colorActive = Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorAccent);
        colorInactive = Utils.getColorAttrDefaultColor(mContext, R.attr.offStateColor);
        colorLabelActive = Utils.getColorAttrDefaultColor(mContext, com.android.internal.R.attr.textColorPrimaryInverse);
        colorLabelInactive = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
        colorSurface = Utils.getColorAttrDefaultColor(mContext, R.attr.colorSurfaceCustom);
        textMediaTitle = Utils.getColorAttrDefaultColor(mContext, R.attr.textMediaTitle);
        textMediaOplus = Utils.getColorAttrDefaultColor(mContext, R.attr.textMediaOplus);
        iconHeaderButtonColor = Utils.getColorAttrDefaultColor(mContext, R.attr.colorHeaderButton);

        Resources resources = mContext.getResources();
        int orientation = getResources().getConfiguration().orientation;
        boolean largeScreenHeaderActive = LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = 0;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        qqsLP.topMargin = 0;
        mHeaderQsPanel.setLayoutParams(qqsLP);

        MarginLayoutParams opQqsLP = (MarginLayoutParams) mOplusQsLayout.getLayoutParams();
        int qqsMarginTop = resources.getDimensionPixelSize(largeScreenHeaderActive ?
                            R.dimen.qqs_layout_margin_top : R.dimen.large_screen_shade_header_min_height);
        opQqsLP.topMargin = qqsMarginTop;
        mOplusQsLayout.setLayoutParams(opQqsLP);

        float qqsExpandY = orientation == Configuration.ORIENTATION_LANDSCAPE ?
                            0 : resources.getDimensionPixelSize(R.dimen.qs_header_height)
                            + resources.getDimensionPixelSize(R.dimen.qs_media_oplus_container_expand_top_margin)
                            - qqsMarginTop;
        TouchAnimator.Builder builderP = new TouchAnimator.Builder()
            .addFloat(mOplusQsLayout, "translationY", 0, qqsExpandY);
        mQQSContainerAnimator = builderP.build();

        // Hide header image in landscape mode
    	  if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
    	      mQsHeaderImageView.setVisibility(View.GONE);
    	  } else {
    	      mQsHeaderImageView.setVisibility(View.VISIBLE);
    	      updateHeaderImage();
    	      applyHeaderBackgroundShadow();
        }
        updateMediaPlayer();
        updateOplusQsSettingsButtonAnim();
        updateEditButtonAnim();
        updateUsageAnimator();
    }

    public void startUpdateInterntTileStateAsync() {
        AsyncTask.execute(new Runnable() {
            public void run() {
                startUpdateInterntTileState();
            }
        });
    }

    public void startUpdateBluetoothTileStateAsync() {
        AsyncTask.execute(new Runnable() {
            public void run() {
                startUpdateBluetoothTileState();
            }
        });
    }

    public void startUpdateInterntTileState() {
        Runnable runnable = mUpdateRunnableInternet;

        if (runnable == null) {
            mUpdateRunnableInternet = new Runnable() {
                public void run() {
                    updateInterntTile();
                    scheduleInternetUpdate();
                }
            };
        } else {
            mHandler.removeCallbacks(runnable);
        }

        scheduleInternetUpdate();
    }

    public void startUpdateBluetoothTileState() {
        Runnable runnable = mUpdateRunnableBluetooth;

        if (runnable == null) {
            mUpdateRunnableBluetooth = new Runnable() {
                public void run() {
                    updateBluetoothTile();
                    scheduleBluetoothUpdate();
                }
            };
        } else {
            mHandler.removeCallbacks(runnable);
        }

        scheduleBluetoothUpdate();
    }

    public void scheduleInternetUpdate() {
        Runnable runnable;
        if ((runnable = mUpdateRunnableInternet) != null) {
            mHandler.postDelayed(runnable, 1000);
        }
    }

    public void scheduleBluetoothUpdate() {
        Runnable runnable;
        if ((runnable = mUpdateRunnableBluetooth) != null) {
            mHandler.postDelayed(runnable, 1000);
        }
    }

    public void updateableRunnerEditBtn() {
        Runnable runnable = mEditTileBtn;
        mEditTileBtn = new Runnable() {
            public void run() {
                updateEditButtonAnim();
            }
        };
    }

    public void scheduleEditBtnUpdate() {
        Runnable runnable;
        if ((runnable = mEditTileBtn) != null) {
            mHandler.postDelayed(runnable, 2500);
        }
        return;
    }

    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
        mEditButton.setOnClickListener(this);
        updateEverything();
    }

    public void setExpansion(boolean forceExpanded, float expansionFraction, float panelTranslationY) {
		final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;

		if (mQQSContainerAnimator != null) {
			mQQSContainerAnimator.setPosition(keyguardExpansionFraction);
		}

        if (mOplusQsSettingsButtonAnimator != null) {
            mOplusQsSettingsButtonAnimator.setPosition(keyguardExpansionFraction);
        }

        if (mEditButtonAnimator != null) {
            mEditButtonAnimator.setPosition(keyguardExpansionFraction);
        }

		if (forceExpanded) {
			setAlpha(expansionFraction);
		} else {
			setAlpha(1);
		}

        if (mUsageText == null) return;
        if (keyguardExpansionFraction == 1.0f) {
            postDelayed(() -> mUsageText.setSelected(true), 1000);
        } else if (keyguardExpansionFraction == 0.0f) {
            mUsageText.setSelected(false);
            mSubId = mCurrentDataSubId;
        }

		mKeyguardExpansionFraction = keyguardExpansionFraction;
	}

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        updateResources();
        updateEverything();
    }

    void updateEverything() {
        post(() -> {
            updateVisibilities();
            setUsageTextDebounced();
        });
    }

    private void updateVisibilities() {
        mUsageText.setVisibility(mShouldShowUsageText ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateSettings() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        updateHeaderImage();
        updateResources();
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    @Override
    public void updateHeader(final Drawable headerImage, final boolean force) {
        post(new Runnable() {
            public void run() {
                doUpdateStatusBarCustomHeader(headerImage, force);
            }
        });
    }

    @Override
    public void disableHeader() {
        post(new Runnable() {
            public void run() {
                mCurrentBackground = null;
                mQsHeaderImageView.setVisibility(View.GONE);
                mHeaderImageEnabled = false;
                updateResources();
            }
        });
    }

    @Override
    public void refreshHeader() {
        post(new Runnable() {
            public void run() {
                doUpdateStatusBarCustomHeader(mCurrentBackground, true);
            }
        });
    }

    @Override
    public void setVisibility(int visibility) {
        mEditButton.setClickable(visibility == View.VISIBLE);
    }

    private void doUpdateStatusBarCustomHeader(final Drawable next, final boolean force) {
        if (next != null) {
            mQsHeaderImageView.setVisibility(View.VISIBLE);
            mCurrentBackground = next;
            setNotificationPanelHeaderBackground(next, force);
            mHeaderImageEnabled = true;
            updateResources();
        } else {
            mCurrentBackground = null;
            mQsHeaderImageView.setVisibility(View.GONE);
            mHeaderImageEnabled = false;
            updateResources();
        }
    }

    private void setNotificationPanelHeaderBackground(final Drawable dw, final boolean force) {
        if (mQsHeaderImageView.getDrawable() != null && !force) {
            Drawable[] arrayDrawable = new Drawable[2];
            arrayDrawable[0] = mQsHeaderImageView.getDrawable();
            arrayDrawable[1] = dw;

            TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            mQsHeaderImageView.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(1000);
        } else {
            mQsHeaderImageView.setImageDrawable(dw);
        }
        applyHeaderBackgroundShadow();
    }

    private void applyHeaderBackgroundShadow() {
        final int headerShadow = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0,
                UserHandle.USER_CURRENT);
        if (mCurrentBackground != null && mQsHeaderImageView.getDrawable() != null) {
            mQsHeaderImageView.setImageAlpha(255 - headerShadow);
        }
    }

    private void updateHeaderImage() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        int headerHeight = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, 142,
                UserHandle.USER_CURRENT);
        int bottomFadeSize = (int) Math.round(headerHeight * 0.555);

        // Set the image header size
        mHeaderImageHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            headerHeight, getContext().getResources().getDisplayMetrics());
        ViewGroup.MarginLayoutParams qsHeaderParams =
            (ViewGroup.MarginLayoutParams) mQsHeaderLayout.getLayoutParams();
        qsHeaderParams.height = mHeaderImageHeight;
        mQsHeaderLayout.setLayoutParams(qsHeaderParams);

        // Set the image fade size (it has to be a 55,5% related to the main size)
        mQsHeaderLayout.setFadeSizes(0,0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            bottomFadeSize, getContext().getResources().getDisplayMetrics()), 0);
    }
}
