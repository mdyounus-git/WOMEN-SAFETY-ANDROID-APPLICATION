package com.example.womensafety.core;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.WindowManager;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.womensafety.R;

/** Small UI helpers shared by screens. */
public final class Ui {

    private Ui() {
    }

    public static int dp(@NonNull Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    @ColorInt
    public static int themeColor(@NonNull Context c, @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId != 0 ? c.getColor(tv.resourceId) : tv.data;
    }

    /**
     * Pads a view by the system bar insets so content clears the status and navigation bars
     * (the app draws edge to edge). Existing padding is preserved.
     */
    public static void padForSystemBars(@NonNull View view, boolean top, boolean bottom) {
        final int l = view.getPaddingLeft();
        final int t = view.getPaddingTop();
        final int r = view.getPaddingRight();
        final int b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(l + bars.left, t + (top ? bars.top : 0), r + bars.right,
                    b + (bottom ? bars.bottom : 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Adds the bottom system bar inset to a view's bottom margin (for floating bars). */
    public static void marginForNavBar(@NonNull View view) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int base = lp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.bottomMargin = base + bars.bottom;
            v.setLayoutParams(p);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** "just now", "4 min ago", "2 hr ago", "Yesterday"... */
    @NonNull
    public static String relativeTime(@NonNull Context c, long timeMs) {
        if (timeMs <= 0) return c.getString(R.string.time_unknown);
        long diff = System.currentTimeMillis() - timeMs;
        if (diff < DateUtils.MINUTE_IN_MILLIS) return c.getString(R.string.time_just_now);
        return DateUtils.getRelativeTimeSpanString(timeMs, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    /** Lets an activity appear over the lock screen and wake the display (SOS / alarms). */
    @SuppressWarnings("deprecation")
    public static void showOverLockScreen(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= 27) {
            activity.setShowWhenLocked(true);
            activity.setTurnScreenOn(true);
        } else {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    /** First letter(s) of a name for avatar bubbles. */
    @NonNull
    public static String initials(@NonNull String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "?";
        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(parts[0].codePointAt(0));
        if (parts.length > 1) sb.appendCodePoint(parts[parts.length - 1].codePointAt(0));
        return sb.toString().toUpperCase();
    }
}
