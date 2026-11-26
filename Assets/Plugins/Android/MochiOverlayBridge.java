package com.mochimate.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

// UnityのC#側から呼び出す、オーバーレイ権限の確認・リクエストと、
// オーバーレイサービスの起動/停止をまとめたヘルパーです。
public class MochiOverlayBridge {

    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    // 権限がない場合、設定画面を開きます(セキュリティ上、自動では許可できません)。
    public static void requestOverlayPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
    }

    public static void startOverlay(Context context) {
        if (!canDrawOverlays(context)) return;
        context.startService(new Intent(context, MochiUnityOverlayService.class));
    }

    public static void stopOverlay(Context context) {
        context.stopService(new Intent(context, MochiUnityOverlayService.class));
    }
}
