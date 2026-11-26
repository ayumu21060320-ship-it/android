package com.mochimate.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.unity3d.player.IUnityPlayerLifecycleEvents;
import com.unity3d.player.UnityPlayerForActivityOrService;

// Unityの描画そのもの(Mochiのシーン)を、ホーム画面に浮かぶ小さなウィンドウとして直接表示します。
// MochiOverlayService(仮の丸アイコン)の本格版です。
public class MochiUnityOverlayService extends Service implements IUnityPlayerLifecycleEvents {

    private WindowManager windowManager;
    private UnityPlayerForActivityOrService unityPlayer;
    private View unityView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        unityPlayer = new UnityPlayerForActivityOrService(this, this);
        unityView = unityPlayer.getFrameLayout();

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                600,
                900,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 80;
        layoutParams.y = 300;

        windowManager.addView(unityView, layoutParams);

        unityPlayer.onStart();
        unityPlayer.onResume();
        unityPlayer.windowFocusChanged(true);
    }

    @Override
    public void onDestroy() {
        if (unityPlayer != null) {
            unityPlayer.onPause();
            unityPlayer.onStop();
            if (unityView != null && windowManager != null) {
                try {
                    windowManager.removeView(unityView);
                } catch (IllegalArgumentException ignored) {
                    // 既に外れている場合は無視
                }
            }
            unityPlayer.destroy();
        }
        super.onDestroy();
    }

    // Unity側でアプリが終了・アンロードされた時
    @Override
    public void onUnityPlayerUnloaded() {
        stopSelf();
    }

    @Override
    public void onUnityPlayerQuitted() {
    }
}
