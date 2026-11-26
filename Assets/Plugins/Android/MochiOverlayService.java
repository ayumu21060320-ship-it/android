package com.mochimate.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

// ホーム画面や他のアプリの上に、Mochiを模した丸いアイコンを浮かせて表示するサービスです。
// 今回はシンプルな円アイコンのみで、Unityの3D表示そのものは行いません。
// ドラッグで移動、ドラッグせずにタップするとアプリ本体を開きます。
public class MochiOverlayService extends Service {

    private WindowManager windowManager;
    private ImageView overlayView;
    private WindowManager.LayoutParams layoutParams;

    private float touchStartX;
    private float touchStartY;
    private int paramStartX;
    private int paramStartY;
    private boolean isDragging;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        showOverlay();
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        overlayView = new ImageView(this);
        overlayView.setImageDrawable(createCircleDrawable());

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                160,
                160,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 100;
        layoutParams.y = 300;

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        paramStartX = layoutParams.x;
                        paramStartY = layoutParams.y;
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;

                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }

                        layoutParams.x = paramStartX + (int) dx;
                        layoutParams.y = paramStartY + (int) dy;
                        windowManager.updateViewLayout(overlayView, layoutParams);
                        return true;
                    }

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            openMainApp();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });

        windowManager.addView(overlayView, layoutParams);
    }

    private void openMainApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private Drawable createCircleDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xFFFFE6A8);
        drawable.setStroke(6, 0xFF333333);
        return drawable;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
    }
}
