package com.capitalfoto.voicediary;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;

public class EmojiUnlockManager {
    private final SharedPreferences prefs;
    private static final String PREFS_NAME = "emoji_unlock_prefs";
    private static final String KEY_ALL_UNLOCKED = "all_emojis_unlocked";

    // НОВЫЕ СМАЙЛЫ (20 шт., без ❤️ так как он уже в базовых)
    public static final String[] NEW_EMOJIS = {
            "😀", "😍", "🔥", "⭐", "🎉",
            "💪", "🤩", "🥳", "😎", "🏆",
            "💲", "💎", "✨", "🌟", "🎈",
            "🍕", "🍺", "🎸", "⚽", "🚀"
    };

    // ДОПОЛНИТЕЛЬНЫЕ СМАЙЛЫ (14 шт.)
    public static final String[] EXTRA_EMOJIS = {
            "📊", "📞", "😢", "🐼", "🦫",
            "🍷", "🏃", "🎾", "😴", "💼",
            "🤪", "✈️", "🚗", "📅"
    };

    // ВСЕ СМАЙЛЫ (3 базовых + 20 новых + 14 дополнительных = 37)
    public static final String[] ALL_EMOJIS = {
            // 3 базовых смайла
            "❤️", "⚡", "😺",
            // 20 новых смайлов (❤️ удален отсюда)
            "😀", "😍", "🔥", "⭐", "🎉",
            "💪", "🤩", "🥳", "😎", "🏆",
            "💲", "💎", "✨", "🌟", "🎈",
            "🍕", "🍺", "🎸", "⚽", "🚀",
            // 14 дополнительных смайлов
            "📊", "📞", "😢", "🐼", "🦫",
            "🍷", "🏃", "🎾", "😴", "💼",
            "🤪", "✈️", "🚗", "📅"
    };

    public EmojiUnlockManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean areAllNewEmojisUnlocked() {
        return prefs.getBoolean(KEY_ALL_UNLOCKED, false);
    }

    public void unlockAllNewEmojis() {
        prefs.edit().putBoolean(KEY_ALL_UNLOCKED, true).apply();
    }

    public void resetUnlock() {
        prefs.edit().remove(KEY_ALL_UNLOCKED).apply();
    }

    public ArrayList<String> getAllAvailableEmojis() {
        ArrayList<String> all = new ArrayList<>();

        if (areAllNewEmojisUnlocked()) {
            // Все смайлы (3 базовых + 20 новых + 14 дополнительных)
            for (String emoji : ALL_EMOJIS) {
                if (!all.contains(emoji)) {
                    all.add(emoji);
                }
            }
        } else {
            // Только 3 базовых смайла
            all.add("❤️");
            all.add("⚡");
            all.add("😺");
        }

        return all;
    }

    public int getBaseCount() {
        return 3;
    }

    public int getNewUnlockedCount() {
        return areAllNewEmojisUnlocked() ? NEW_EMOJIS.length + EXTRA_EMOJIS.length : 0;
    }

    public int getTotalCount() {
        if (areAllNewEmojisUnlocked()) {
            return ALL_EMOJIS.length;
        } else {
            return 3;
        }
    }
}