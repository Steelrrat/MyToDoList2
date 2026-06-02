package com.capitalfoto.voicediary;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoader;
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener;  // ← ДОБАВИТЬ ЭТОТ ИМПОРТ

public class YandexRewardManager {
    private static final String TAG = "YandexReward";
    private static final String AD_UNIT_ID = "demo-rewarded-yandex";

    private RewardedAd rewardedAd;
    private final Context context;
    private Runnable onRewardCallback;

    public YandexRewardManager(Context context) {
        this.context = context;
        loadRewardedAd();
    }

    private void loadRewardedAd() {
        RewardedAdLoader loader = new RewardedAdLoader(context);

        AdRequest adRequest = new AdRequest.Builder(AD_UNIT_ID).build();

        // ИСПРАВЛЕНО: убираем RewardedAdLoader. перед интерфейсом
        loader.loadAd(adRequest, new RewardedAdLoadListener() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                Log.d(TAG, "✅ Реклама загружена");
                rewardedAd = ad;

                rewardedAd.setAdEventListener(new RewardedAdEventListener() {
                    @Override
                    public void onRewarded(@NonNull Reward reward) {
                        Log.d(TAG, "🎁 Награда получена!");
                        if (onRewardCallback != null) {
                            onRewardCallback.run();
                            onRewardCallback = null;
                        }
                    }

                    @Override
                    public void onAdShown() {
                        Log.d(TAG, "📺 Реклама показана");
                    }

                    @Override
                    public void onAdFailedToShow(@NonNull com.yandex.mobile.ads.common.AdError adError) {
                        Log.e(TAG, "❌ Ошибка показа: " + adError.getDescription());
                    }

                    @Override
                    public void onAdDismissed() {
                        Log.d(TAG, "❌ Реклама закрыта");
                        loadRewardedAd();
                    }

                    @Override
                    public void onAdClicked() {
                        Log.d(TAG, "👆 Клик по рекламе");
                    }

                    @Override
                    public void onAdImpression(@NonNull com.yandex.mobile.ads.common.ImpressionData impressionData) {
                        Log.d(TAG, "📊 Impression зафиксировано");
                    }
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull AdRequestError error) {
                Log.e(TAG, "❌ Ошибка загрузки: " + error.getDescription());
            }
        });
    }

    public void showRewardedAd(Runnable onReward, Runnable onFail) {
        this.onRewardCallback = onReward;

        if (rewardedAd != null && context instanceof Activity) {
            rewardedAd.show((Activity) context);
        } else if (onFail != null) {
            onFail.run();
            loadRewardedAd();
        }
    }

    public void destroy() {
        if (rewardedAd != null) {
            rewardedAd.setAdEventListener(null);
            rewardedAd = null;
        }
        onRewardCallback = null;
    }
}