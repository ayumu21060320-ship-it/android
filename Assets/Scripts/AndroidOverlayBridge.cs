using UnityEngine;

// Androidのネイティブオーバーレイ(ホーム画面に浮かぶ丸いMochi)を操作するためのブリッジです。
// 実際の表示・権限リクエストはAssets/Plugins/Android内のJavaコードが行います。
// Android実機以外(Unityエディタなど)では何もせず、ログだけ出します。
public class AndroidOverlayBridge : MonoBehaviour
{
#if UNITY_ANDROID && !UNITY_EDITOR
    private const string BridgeClassName = "com.mochimate.overlay.MochiOverlayBridge";

    private static AndroidJavaObject GetCurrentActivity()
    {
        using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            return unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
        }
    }
#endif

    public bool CanDrawOverlays()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaObject activity = GetCurrentActivity())
        using (var bridge = new AndroidJavaClass(BridgeClassName))
        {
            return bridge.CallStatic<bool>("canDrawOverlays", activity);
        }
#else
        return false;
#endif
    }

    // 権限がなければ、許可をお願いする設定画面を開きます。
    public void RequestOverlayPermission()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaObject activity = GetCurrentActivity())
        using (var bridge = new AndroidJavaClass(BridgeClassName))
        {
            bridge.CallStatic("requestOverlayPermission", activity);
        }
#else
        Debug.Log("オーバーレイ権限のリクエストはAndroid実機でのみ動作します。");
#endif
    }

    public void StartOverlay()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaObject activity = GetCurrentActivity())
        using (var bridge = new AndroidJavaClass(BridgeClassName))
        {
            bridge.CallStatic("startOverlay", activity);
        }
#else
        Debug.Log("オーバーレイの表示はAndroid実機でのみ動作します。");
#endif
    }

    public void StopOverlay()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using (AndroidJavaObject activity = GetCurrentActivity())
        using (var bridge = new AndroidJavaClass(BridgeClassName))
        {
            bridge.CallStatic("stopOverlay", activity);
        }
#else
        Debug.Log("オーバーレイの停止はAndroid実機でのみ動作します。");
#endif
    }
}
