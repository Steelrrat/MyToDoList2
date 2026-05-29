package com.capitalfoto.voicediary;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import androidx.annotation.NonNull;
import com.my.target.ads.RewardedAd;
import com.my.target.common.models.IAdLoadingError;
import com.my.target.ads.Reward;
import com.my.target.common.MyTargetManager;

public class RewardManager {
    private static final String TAG = "RewardManager";

    // ============================================================
    // ⚠️ ВАШ РЕАЛЬНЫЙ SLOT_ID из кабинета VK Ads
    // ============================================================
   private static final int SLOT_ID = 2015946;

    private RewardedAd rewardedAd;
    private Context context;
    private Runnable onRewardCallback;
    private Runnable onFailCallback;
    private boolean isLoading = false;

    public RewardManager(Context context) {
        this.context = context;

        // ============================================================
        // ✅ ПРАВИЛЬНО: setDebugMode ДО инициализации SDK
        // Согласно официальной документации VK Ads
        // ============================================================
        MyTargetManager.setDebugMode(true);

        initVkAds();
    }

    private void initVkAds() {
        // Инициализация рекламы
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
                }

                @Override
                public void onNoAd(@NonNull IAdLoadingError error, @NonNull RewardedAd ad) {
                    Log.e(TAG, "❌ Rewarded Ad failed: " + error.getMessage());
                    isLoading = false;

                    // Если была награда на подходе - выдаём её
                    if (onRewardCallback != null) {
                        Runnable callback = onRewardCallback;
                        onRewardCallback = null;
                        if (callback != null) callback.run();
                    }

                    loadRewardedAd();  // Перезагружаем
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
                    loadRewardedAd();  // Загружаем новую
                }

                @Override
                public void onFailedToShow(@NonNull RewardedAd ad) {
                    Log.e(TAG, "❌ Rewarded Ad failed to show");
                    isLoading = false;

                    // При ошибке показа - выдаём награду
                    if (onRewardCallback != null) {
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
        }
    }

    public void showRewardedAd(Runnable onReward, Runnable onFail) {
        this.onRewardCallback = onReward;
        this.onFailCallback = onFail;

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
            rewardedAd.destroy();
            rewardedAd = null;
        }
        onRewardCallback = null;
        onFailCallback = null;
        isLoading = false;
        context = null;
    }
}