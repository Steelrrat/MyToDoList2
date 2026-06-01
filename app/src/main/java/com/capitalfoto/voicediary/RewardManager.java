package com.capitalfoto.voicediary;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.my.target.ads.RewardedAd;
import com.my.target.common.MyTargetConfig;
import com.my.target.common.models.IAdLoadingError;
import com.my.target.ads.Reward;
import com.my.target.common.MyTargetManager;

public class RewardManager {
    private static final String TAG = "RewardManager";
    private static final int SLOT_ID = 2015946;
    private static final int MAX_RETRIES = 3;
    private static final boolean IS_DEBUG = false;

    private RewardedAd rewardedAd;
    private Context context;
    private Runnable onRewardCallback;
    private Runnable onFailCallback;
    private boolean isLoading = false;
    private int retryCount = 0;

    public RewardManager(Context context) {
        this.context = context;
        if (IS_DEBUG) {
            MyTargetManager.setDebugMode(true);
        }
        initVkAds();
    }

    private void initVkAds() {
        MyTargetConfig.Builder builder = new MyTargetConfig.Builder();
        if (IS_DEBUG) {
            builder.withTestDevices("9bbf3c1e-931c-4512-84ef-c5a7a4be0344");
        }
        MyTargetManager.setSdkConfig(builder.build());
        loadRewardedAd();
    }

    private void loadRewardedAd() {
        if (isLoading) return;
        isLoading = true;

        try {
            rewardedAd = new RewardedAd(SLOT_ID, context);
            rewardedAd.setListener(new RewardedAd.RewardedAdListener() {
                @Override
                public void onLoad(@NonNull RewardedAd ad) {
                    Log.d(TAG, "✅ Rewarded Ad loaded");
                    isLoading = false;
                    retryCount = 0;
                }

                @Override
                public void onNoAd(@NonNull IAdLoadingError error, @NonNull RewardedAd ad) {
                    Log.e(TAG, "❌ Rewarded Ad failed: " + error.getMessage());
                    isLoading = false;
                    retryCount++;

                    if (IS_DEBUG) {
                        Log.d(TAG, "DEBUG MODE: Giving reward without ad");
                        if (onRewardCallback != null) {
                            Runnable callback = onRewardCallback;
                            onRewardCallback = null;
                            if (callback != null) callback.run();
                        }
                        return;
                    }

                    if (retryCount < MAX_RETRIES) {
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> loadRewardedAd(), 30000);
                    } else if (onRewardCallback != null) {
                        Runnable callback = onRewardCallback;
                        onRewardCallback = null;
                        if (callback != null) callback.run();
                    }
                }

                @Override
                public void onClick(@NonNull RewardedAd ad) {
                    Log.d(TAG, "👆 Rewarded Ad clicked");
                }

                @Override
                public void onDisplay(@NonNull RewardedAd ad) {
                    Log.d(TAG, "📺 Rewarded Ad displayed");
                }

                @Override
                public void onDismiss(@NonNull RewardedAd ad) {
                    Log.d(TAG, "❌ Rewarded Ad dismissed");
                    loadRewardedAd();
                }

                @Override
                public void onFailedToShow(@NonNull RewardedAd ad) {
                    Log.e(TAG, "❌ Rewarded Ad failed to show");
                    isLoading = false;

                    if (IS_DEBUG) {
                        Log.d(TAG, "DEBUG MODE: Giving reward after show failure");
                        if (onRewardCallback != null) {
                            Runnable callback = onRewardCallback;
                            onRewardCallback = null;
                            if (callback != null) callback.run();
                        }
                    } else if (onRewardCallback != null) {
                        Runnable callback = onRewardCallback;
                        onRewardCallback = null;
                        if (callback != null) callback.run();
                    }
                }

                @Override
                public void onReward(@NonNull Reward reward, @NonNull RewardedAd ad) {
                    Log.d(TAG, "🎁 Reward received!");
                    if (onRewardCallback != null) {
                        Runnable callback = onRewardCallback;
                        onRewardCallback = null;
                        if (callback != null) callback.run();
                    }
                }
            });
            rewardedAd.load();
            Log.d(TAG, "Loading rewarded ad with SLOT_ID: " + SLOT_ID);
        } catch (Exception e) {
            Log.e(TAG, "Error creating RewardedAd", e);
            isLoading = false;
            if (IS_DEBUG && onRewardCallback != null) {
                Runnable callback = onRewardCallback;
                onRewardCallback = null;
                if (callback != null) callback.run();
            }
        }
    }

    public void showRewardedAd(Runnable onReward, Runnable onFail) {
        this.onRewardCallback = onReward;
        this.onFailCallback = onFail;

        if (IS_DEBUG) {
            Log.d(TAG, "DEBUG MODE: Giving reward immediately");
            if (onRewardCallback != null) {
                onRewardCallback.run();
                onRewardCallback = null;
            }
            return;
        }

        if (rewardedAd != null) {
            rewardedAd.show();
        } else {
            Log.w(TAG, "RewardedAd is null");
            if (onFailCallback != null) {
                onFailCallback.run();
            }
        }
    }

    public void destroy() {
        Log.d(TAG, "Destroying RewardManager");
        if (rewardedAd != null) {
            rewardedAd.setListener(null);
            rewardedAd.destroy();
            rewardedAd = null;
        }
        onRewardCallback = null;
        onFailCallback = null;
        isLoading = false;
        retryCount = 0;
        context = null;
    }
}