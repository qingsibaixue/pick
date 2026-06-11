package com.example.goldmod;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.ArrayList;
import java.util.List;

/**
 * 去广告 + 永久会员 + 金币视频免费看
 *
 * 金币视频播放链（长视频 VideoPlayActivity）：
 *   getVideoById → videoBean(canWatch=false, reasonType=2)
 *   → playVideo(): reasonType!=0 → showBlockingPopup() 或 setStatus(2) 显示购买层
 *   → 用户点"10金购买" → buyOrLeaseVideo() → 服务端 code=1019 → createGoldDialog()
 *
 * 关键修复点：
 *   1. playVideo 前强制 reasonType=0 / canWatch=true，绕过购买层
 *   2. playerSubsequentProcessing 去掉 isVIP 门槛，始终 startPlay
 *   3. VideoPlayer.startPlay 改用 playBaseFull2(videoId) 直链（服务端按 id 解码）
 *   4. 拦截 createGoldDialog 等弹窗 + 购买 API 强制返回 200
 *   5. videoCanWatch API 强制 canWatch=true
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "GOLD";
    private static final String TARGET_PACKAGE = "z05guu.ynyiza.ehlown.zz8xy.d1780970667328100427";

    private static final String BEAN_VIDEO_BEAN = "com.androidx.lv.base.bean.VideoBean";
    private static final String BEAN_CAN_WATCH = "com.androidx.lv.base.bean.CanWatchBean";
    private static final String BEAN_USER_INFO = "com.androidx.lv.base.bean.UserInfo";
    private static final String BEAN_USER_ACCOUNT = "com.androidx.lv.base.bean.UserAccount";

    private static final String UTILS_AD_NEW = "com.grass.mh.utils.AdNewUtils";
    private static final String UTILS_FAST_DIALOG = "com.grass.mh.utils.FastDialogUtils";
    private static final String VIDEO_POPUP_RESOLVER = "com.grass.mh.video.VideoPopupResolver";
    private static final String PLAYER_VIDEO = "com.grass.mh.player.VideoPlayer";
    private static final String PLAYER_BRUSH = "com.grass.mh.player.BrushVideoPlayer";
    private static final String PLAYER_PHOTO = "com.grass.mh.player.PhotoTikTokPlayer";
    private static final String ACT_VIDEO_PLAY = "com.grass.mh.ui.home.VideoPlayActivity";
    private static final String ACT_SHORT_VIDEO = "com.grass.mh.ui.home.ShortVideoPlayActivity";
    private static final String ACT_SPLASH = "com.grass.mh.ui.SplashActivity";
    private static final String HTTP_CALLBACK = "com.androidx.lv.base.http.callback.HttpCallback";
    private static final String BASE_RES = "com.androidx.lv.base.http.BaseRes";
    private static final String URL_MANAGER = "com.androidx.lv.base.http.UrlManager";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log("=== [" + TAG + "] 模块进入: " + lpparam.packageName);

        hookToast(lpparam);
        hookVIP(lpparam);
        hookFreeWatches(lpparam);
        hookGoldAndBala(lpparam);
        hookVideoBean(lpparam);
        hookPopupResolver(lpparam);
        hookPlayVideo(lpparam);
        hookPlayerSubsequentProcessing(lpparam);
        hookVideoPlayerStartPlay(lpparam);
        hookFastDialog(lpparam);
        hookHttpResponses(lpparam);
        hookShortVideoPopup(lpparam);
        hookPhotoTikTokCanWatch(lpparam);
        hookBrushCanWatch(lpparam);
        hookBrushVideoBuy(lpparam);
        hookAdNewUtils(lpparam);
        hookSetFullAd(lpparam);
        hookSetAd(lpparam);
        hookShowAdView(lpparam);
        hookSplashAd(lpparam);

        XposedBridge.log("=== [" + TAG + "] 所有 hook 注册完毕");
    }

    // ============================================================
    // Toast
    // ============================================================
    private void hookToast(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
                    "attach", Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context ctx = (Context) param.args[0];
                            Toast.makeText(ctx, "去广告模块已激活", Toast.LENGTH_SHORT).show();
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK toast");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL toast: " + t);
        }
    }

    // ============================================================
    // 永久会员
    // ============================================================
    private void hookVIP(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_USER_INFO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "isVIP", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "getVipType", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return 6;
                }
            });
            XposedBridge.log("[" + TAG + "] OK isVIP=true vipType=6");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL VIP: " + t);
        }
    }

    private void hookFreeWatches(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_USER_INFO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "getFreeWatches", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return -1;
                }
            });
            XposedBridge.log("[" + TAG + "] OK freeWatches=-1");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL freeWatches: " + t);
        }
    }

    private void hookGoldAndBala(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_USER_ACCOUNT, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "getGold", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return 9999999.0;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "getBala", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return 9999999.0;
                }
            });
            XposedBridge.log("[" + TAG + "] OK gold/bala=9999999");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL gold: " + t);
        }
    }

    // ============================================================
    // VideoBean：所有读取路径均返回"可观看"
    // ============================================================
    private void hookVideoBean(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_VIDEO_BEAN, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(cls, "isCanWatch", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "getReasonType", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return 0;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "isBuy", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
            // playPath 为空时 playVideo 会直接 return，用 videoUrl 兜底
            XposedHelpers.findAndHookMethod(cls, "getPlayPath", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String path = (String) param.getResult();
                    if (!TextUtils.isEmpty(path)) {
                        return;
                    }
                    Object bean = param.thisObject;
                    String videoUrl = (String) XposedHelpers.callMethod(bean, "getVideoUrl");
                    if (!TextUtils.isEmpty(videoUrl)) {
                        param.setResult(videoUrl);
                    } else {
                        param.setResult("bypass");
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK VideoBean canWatch/reason/buy/playPath");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL VideoBean: " + t);
        }
    }

    // ============================================================
    // VideoPopupResolver.resolve → NONE
    // ============================================================
    private void hookPopupResolver(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> resolverClass = XposedHelpers.findClass(VIDEO_POPUP_RESOLVER, lpparam.classLoader);
            Class<?> popupTypeClass = null;
            for (Class<?> c : resolverClass.getDeclaredClasses()) {
                if (c.isEnum() && "PopupType".equals(c.getSimpleName())) {
                    popupTypeClass = c;
                    break;
                }
            }
            if (popupTypeClass == null) {
                XposedBridge.log("[" + TAG + "] FAIL PopupType enum 未找到");
                return;
            }
            final Object popupTypeNone = popupTypeClass.getEnumConstants()[0];

            XposedHelpers.findAndHookMethod(resolverClass, "resolve",
                    XposedHelpers.findClass(BEAN_VIDEO_BEAN, lpparam.classLoader),
                    double.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[" + TAG + "] resolve -> NONE");
                            return popupTypeNone;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK PopupResolver.resolve->NONE");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL PopupResolver: " + t);
        }
    }

    // ============================================================
    // VideoPlayActivity.playVideo：进入前强制修正 bean 字段
    // 日志显示 reasonType=2 时即使 resolve 返回 NONE 仍会 setStatus(2) 显示购买层
    // ============================================================
    private void hookPlayVideo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_VIDEO_PLAY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "playVideo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object activity = param.thisObject;
                    Object videoBean = XposedHelpers.getObjectField(activity, "videoBean");
                    if (videoBean == null) {
                        return;
                    }
                    XposedHelpers.callMethod(videoBean, "setReasonType", 0);
                    XposedHelpers.callMethod(videoBean, "setCanWatch", true);
                    XposedHelpers.callMethod(videoBean, "setBuy", true);

                    String playPath = (String) XposedHelpers.callMethod(videoBean, "getPlayPath");
                    if (TextUtils.isEmpty(playPath)) {
                        String videoUrl = (String) XposedHelpers.callMethod(videoBean, "getVideoUrl");
                        XposedHelpers.callMethod(videoBean, "setPlayPath",
                                TextUtils.isEmpty(videoUrl) ? "bypass" : videoUrl);
                    }
                    XposedHelpers.setIntField(activity, "status", 0);
                    XposedBridge.log("[" + TAG + "] playVideo 前置修正: reasonType=0 canWatch=true");
                }
            });
            XposedBridge.log("[" + TAG + "] OK VideoPlayActivity.playVideo");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL playVideo: " + t);
        }
    }

    // ============================================================
    // playerSubsequentProcessing：原逻辑仅 isVIP() 才 startPlay，改为始终播放
    // ============================================================
    private void hookPlayerSubsequentProcessing(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_VIDEO_PLAY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "playerSubsequentProcessing", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object activity = param.thisObject;
                    Object binding = XposedHelpers.getObjectField(activity, "binding");
                    if (binding == null) {
                        return;
                    }
                    Object player = XposedHelpers.getObjectField(binding, "player");
                    if (player != null) {
                        XposedHelpers.callMethod(player, "startPlay");
                        XposedBridge.log("[" + TAG + "] playerSubsequentProcessing -> startPlay");
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK playerSubsequentProcessing");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL playerSubsequentProcessing: " + t);
        }
    }

    // ============================================================
    // VideoPlayer.startPlay：改用 /api/m3u8/decode/by/id?videoId= 直链
    // 原逻辑走 authPath(videoUrl) 可能只有预览地址
    // ============================================================
    private void hookVideoPlayerStartPlay(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_VIDEO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "startPlay", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    Object player = param.thisObject;
                    Object videoBean = XposedHelpers.getObjectField(player, "videoBean");
                    if (videoBean == null) {
                        return null;
                    }
                    int videoId = (int) XposedHelpers.callMethod(videoBean, "getVideoId");
                    Object urlMgr = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass(URL_MANAGER, lpparam.classLoader), "getInsatance");
                    String url = (String) XposedHelpers.callMethod(urlMgr, "playBaseFull2", videoId);

                    XposedHelpers.callMethod(player, "setSpeed", 1.0f, true);
                    XposedHelpers.callMethod(player, "setUp", url, true, "");
                    XposedHelpers.callMethod(player, "startPlayLogic");
                    XposedBridge.log("[" + TAG + "] VideoPlayer.startPlay url=" + url);
                    return null;
                }
            });
            XposedBridge.log("[" + TAG + "] OK VideoPlayer.startPlay");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL VideoPlayer.startPlay: " + t);
        }
    }

    // ============================================================
    // FastDialogUtils：拦截"购买失败/金币不足"弹窗（图中弹窗）
    // createGoldDialog(context) → "购买失败" + "金币不足暂时无法购买"
    // ============================================================
    private void hookFastDialog(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(UTILS_FAST_DIALOG, lpparam.classLoader);

            XC_MethodReplacement block = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    XposedBridge.log("[" + TAG + "] 拦截金币弹窗: " + param.method.getName());
                    return null;
                }
            };

            XposedHelpers.findAndHookMethod(cls, "createGoldDialog", Context.class, block);
            XposedHelpers.findAndHookMethod(cls, "createGoldDialog", Context.class, String.class, String.class, block);
            XposedHelpers.findAndHookMethod(cls, "createVideoGoldNotEnoughDialog", Activity.class, block);

            XposedBridge.log("[" + TAG + "] OK FastDialogUtils 金币弹窗拦截");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL FastDialog: " + t);
        }
    }

    // ============================================================
    // HTTP 响应：购买失败(1019)强制成功；canWatch 强制 true
    // ============================================================
    private void hookHttpResponses(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> responseClass = XposedHelpers.findClass("com.lzy.okgo.model.Response", lpparam.classLoader);
            Class<?> baseResClass = XposedHelpers.findClass(BASE_RES, lpparam.classLoader);
            Class<?> canWatchClass = XposedHelpers.findClass(BEAN_CAN_WATCH, lpparam.classLoader);
            Class<?> httpCallbackClass = XposedHelpers.findClass(HTTP_CALLBACK, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(httpCallbackClass, "onSuccess", responseClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String tag = (String) XposedHelpers.callMethod(param.thisObject, "getTag");
                    if (tag == null) {
                        return;
                    }
                    Object response = param.args[0];
                    Object body = XposedHelpers.callMethod(response, "body");
                    if (body == null || !baseResClass.isInstance(body)) {
                        return;
                    }

                    int code = (int) XposedHelpers.callMethod(body, "getCode");

                    if ("userBuyVideo".equals(tag) || "videoBuy".equals(tag)) {
                        if (code != 200) {
                            XposedBridge.log("[" + TAG + "] " + tag + " code=" + code + " -> 强制200");
                            XposedHelpers.callMethod(body, "setCode", 200);
                            Object data = XposedHelpers.callMethod(body, "getData");
                            if (data == null) {
                                data = canWatchClass.newInstance();
                                XposedHelpers.callMethod(body, "setData", data);
                            }
                            XposedHelpers.callMethod(data, "setCanWatch", true);
                            XposedHelpers.callMethod(data, "setReasonType", 0);
                        }
                    } else if ("videoCanWatch".equals(tag) && code == 200) {
                        Object data = XposedHelpers.callMethod(body, "getData");
                        if (data != null) {
                            XposedHelpers.callMethod(data, "setCanWatch", true);
                            XposedBridge.log("[" + TAG + "] videoCanWatch -> canWatch=true");
                        }
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK HttpCallback.onSuccess");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL HttpCallback: " + t);
        }
    }

    // ============================================================
    // 短视频
    // ============================================================
    private void hookShortVideoPopup(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_SHORT_VIDEO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "showBlockingPopup",
                    XposedHelpers.findClass(BEAN_VIDEO_BEAN, lpparam.classLoader),
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return Boolean.FALSE;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK ShortVideoPlayActivity.showBlockingPopup");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL ShortVideoPopup: " + t);
        }
    }

    private void hookPhotoTikTokCanWatch(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_PHOTO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "videoCanWatch", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object bean = XposedHelpers.getObjectField(param.thisObject, "bean");
                    if (bean != null) {
                        try {
                            XposedHelpers.callMethod(bean, "setCanWatch", true);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK PhotoTikTokPlayer.videoCanWatch");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL PhotoTikTok: " + t);
        }
    }

    private void hookBrushCanWatch(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_BRUSH, lpparam.classLoader);
            XC_MethodHook forceCanWatch = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object videoBean = XposedHelpers.getObjectField(param.thisObject, "videoBean");
                    if (videoBean != null) {
                        XposedHelpers.callMethod(videoBean, "setCanWatch", true);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(cls, "canWatch", forceCanWatch);
            XposedHelpers.findAndHookMethod(cls, "videoCanWatch", forceCanWatch);
            XposedBridge.log("[" + TAG + "] OK BrushVideoPlayer.canWatch/videoCanWatch");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL BrushCanWatch: " + t);
        }
    }

    // BrushVideoPlayer.videoBuy：购买成功后走 startPlay
    private void hookBrushVideoBuy(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_BRUSH, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "videoBuy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object videoBean = XposedHelpers.getObjectField(param.thisObject, "videoBean");
                    if (videoBean != null) {
                        XposedHelpers.callMethod(videoBean, "setCanWatch", true);
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK BrushVideoPlayer.videoBuy");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL BrushVideoBuy: " + t);
        }
    }

    // ============================================================
    // 广告
    // ============================================================
    private void hookAdNewUtils(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(UTILS_AD_NEW, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(cls, "getAdWeight", String.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedHelpers.findAndHookMethod(cls, "getStartAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return new ArrayList();
                        }
                    });
            XposedHelpers.findAndHookMethod(cls, "getAdsWeight", String.class, int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return new ArrayList();
                        }
                    });
            XposedHelpers.findAndHookMethod(cls, "getAdSort", String.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return new ArrayList();
                        }
                    });
            XposedHelpers.findAndHookMethod(cls, "getStationAd", int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedHelpers.findAndHookMethod(cls, "getAppStationAd", int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK AdNewUtils");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL AdNewUtils: " + t);
        }
    }

    private void hookSetFullAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_VIDEO_PLAY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "setFullAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK setFullAd");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL setFullAd: " + t);
        }
    }

    private void hookSetAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_VIDEO_PLAY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "setAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK setAd");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL setAd: " + t);
        }
    }

    private void hookShowAdView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_VIDEO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "showAdView",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK showAdView");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL showAdView: " + t);
        }
    }

    private void hookSplashAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_SPLASH, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "adShowMine", List.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK SplashActivity.adShowMine");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL SplashAd: " + t);
        }
    }
}
