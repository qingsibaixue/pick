package com.example.goldmod;

import android.content.Context;
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
 * 去广告 + 永久会员 + 金币无限 + 无弹窗
 *
 * 架构理解 (基于完整源码阅读)：
 * - AdNewUtils 全部广告位从 SpUtils.getString(Key.AD_INFO) 读 (JSON List<AdBaseInfoBean>)
 * - VideoPopupResolver.resolve() 第一关: if (videoBean.isCanWatch()) return NONE
 * - UserInfo.isVIP() 实际实现: return this.vipType >= 1;
 * - VideoPlayActivity.playVideo() line 692: if (reasonType != 0) 弹窗 → return
 * - 视频播放链: playVideo → playerSubsequentProcessing → if (isVIP()) player.startPlay()
 * - setFullAd() 第一行: if (userInfo.isVIP()) return (跳过 LONG_VIDEO_PLAY_START 广告)
 * - initPopAdDialog() 第一行: if (userInfo.isVIP()) ... schedulePlayVideoIfReady() (跳过 START_POP_UP 弹窗)
 *
 * 目标包: z05guu.ynyiza.ehlown.zz8xy.d1780970667328100427 (混淆 R 类)
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "GOLD";
    private static final String TARGET_PACKAGE = "z05guu.ynyiza.ehlown.zz8xy.d1780970667328100427";

    // ====== 用户核心身份 ======
    private static final String BEAN_VIDEO_BEAN = "com.androidx.lv.base.bean.VideoBean";
    private static final String BEAN_USER_INFO = "com.androidx.lv.base.bean.UserInfo";
    private static final String BEAN_USER_ACCOUNT = "com.androidx.lv.base.bean.UserAccount";

    // ====== 业务核心 ======
    private static final String UTILS_AD_NEW = "com.grass.mh.utils.AdNewUtils";
    private static final String UTILS_FAST_DIALOG = "com.grass.mh.utils.FastDialogUtils";
    private static final String VIDEO_POPUP_RESOLVER = "com.grass.mh.video.VideoPopupResolver";
    private static final String PLAYER_VIDEO = "com.grass.mh.player.VideoPlayer";
    private static final String PLAYER_BRUSH = "com.grass.mh.player.BrushVideoPlayer";
    private static final String PLAYER_PHOTO = "com.grass.mh.player.PhotoTikTokPlayer";
    private static final String ACT_VIDEO_PLAY = "com.grass.mh.ui.home.VideoPlayActivity";
    private static final String ACT_SHORT_VIDEO = "com.grass.mh.ui.home.ShortVideoPlayActivity";
    private static final String ACT_SPLASH = "com.grass.mh.ui.SplashActivity";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log("=== [" + TAG + "] 模块进入: " + lpparam.packageName);

        // ====== 0. Toast 提示模块加载成功 ======
        hookToast(lpparam);

        // ====== 1. 会员：UserInfo.isVIP() 永久 true ======
        // 副作用: setFullAd / initPopAdDialog 第一行 isVIP()=true → 跳过广告
        //         playerSubsequentProcessing isVIP()=true → 直接 player.startPlay()
        hookVIP(lpparam);

        // ====== 2. 免费次数无限 ======
        // VideoPlayActivity.playVideo() line 703: freeWatches==-1 → 不弹 freeLimitDialog
        hookFreeWatches(lpparam);

        // ====== 3. 金币/余额无限 ======
        // 副作用: VideoPopupResolver.resolve(goldBalance) → GOLD_CONFIRM 而不是 GOLD_NOT_ENOUGH
        hookGoldAndBala(lpparam);

        // ====== 4. 视频 Bean 核心：canWatch/reasonType/videoType 全部友好 ======
        // VideoPopupResolver 第一关: if (canWatch) return NONE  → 全部 NONE (所有弹窗消失)
        // VideoPlayActivity.playVideo() line 692: if (reasonType != 0) → 永远 0, 不弹窗分支
        // VideoPopupResolver: videoType==2 && !isBuy → 永远走 NONE
        hookVideoBean(lpparam);

        // ====== 5. 弹窗判定核心：VideoPopupResolver.resolve 永远返回 NONE ======
        // 双保险 (即使上面 hookVideoBean 有遗漏, 这里也兜底)
        hookPopupResolver(lpparam);

        // ====== 6. 短视频弹窗判定核心：ShortVideoPlayActivity.showBlockingPopup 永远 false ======
        // 短视频走的是 lambda$playCurrent$3 → showBlockingPopup → NONE 时不弹
        hookShortVideoPopup(lpparam);

        // ====== 7. 短视频播放链：PhotoTikTokPlayer.videoCanWatch 强制 setCanWatch(true) ======
        // 短视频 onSuccess 拿到 baseRes.data.getCanWatch() 直接 setCanWatch, 这里 hook 让它强制 true
        hookPhotoTikTokCanWatch(lpparam);

        // ====== 8. 长视频播放链：BrushVideoPlayer.canWatch() 强制 setCanWatch(true) ======
        hookBrushCanWatch(lpparam);

        // ====== 9. 全广告消灭：AdNewUtils.getAdWeight/getStartAd/getAdsWeight/getAdSort 全部返空 ======
        hookAdNewUtils(lpparam);

        // ====== 10. VideoPlayActivity.setFullAd (LONG_VIDEO_PLAY_START 长视频开始倒计时广告) ======
        hookSetFullAd(lpparam);

        // ====== 11. VideoPlayActivity.setAd (INSERT_IMAGE 插入图片广告) ======
        hookSetAd(lpparam);

        // ====== 12. VideoPlayer.showAdView (LONG_VIDEO_PAUSE 暂停广告) ======
        hookShowAdView(lpparam);

        // ====== 13. SplashActivity 开屏广告 (adShowMine) ======
        hookSplashAd(lpparam);

        // ====== 14. BaseApp.adFinish 静态字段跳过 (VideoPlayer.showAd 已通过 hook adInfoBean=null 兜底) ======
        // BaseApp.adFinish 是 public static boolean 字段，不是方法，不能 hook
        // VideoPlayer.showAd 的判断: if (adInfoBean==null) return
        //   adInfoBean 在 init() 里从 getAdWeight("PLAY_PAGE_THUMBNAIL") 来
        //   → hook getAdWeight 返 null → adInfoBean 永远 null → 永远不会触发广告 show
        // 所以不需要再专门 hook adFinish
        XposedBridge.log("[" + TAG + "] SKIP BaseApp.adFinish (字段访问,已通过 hook getAdWeight 兜底)");
    }

    // ============================================================
    // 0. Toast 提示
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
    // 1. 永久会员: UserInfo.isVIP() → true
    // ============================================================
    private void hookVIP(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_USER_INFO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "isVIP", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return Boolean.TRUE;
                }
            });
            // 同时把 getVipType() 设为 6 (永久会员), 万一有代码读 getVipType()
            XposedHelpers.findAndHookMethod(cls, "getVipType", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return 6;
                }
            });
            XposedBridge.log("[" + TAG + "] OK isVIP=true vipType=6");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL VIP: " + t);
        }
    }

    // ============================================================
    // 2. 免费次数无限: UserInfo.getFreeWatches() → -1
    // ============================================================
    private void hookFreeWatches(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_USER_INFO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "getFreeWatches", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return -1;
                }
            });
            XposedBridge.log("[" + TAG + "] OK freeWatches=-1");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL freeWatches: " + t);
        }
    }

    // ============================================================
    // 3. 金币/余额无限: UserAccount.getGold/getBala → 大数
    // ============================================================
    private void hookGoldAndBala(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_USER_ACCOUNT, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "getGold", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return 9999999.0;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "getBala", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return 9999999.0;
                }
            });
            XposedBridge.log("[" + TAG + "] OK gold/bala=9999999");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL gold: " + t);
        }
    }

    // ============================================================
    // 4. VideoBean 字段全部友好
    //    isCanWatch=true   → 绕过 VideoPopupResolver 第一关
    //    getReasonType()=0 → 绕过 playVideo 弹窗分支
    //    isBuy()=true      → 绕过 videoType==2 && !isBuy 判定
    //    getVideoType()=0  → 强制免费视频类型
    // ============================================================
    private void hookVideoBean(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BEAN_VIDEO_BEAN, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(cls, "isCanWatch", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return Boolean.TRUE;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "getReasonType", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return 0;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "isBuy", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return Boolean.TRUE;
                }
            });
            XposedHelpers.findAndHookMethod(cls, "getVideoType", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return 0;
                }
            });
            XposedBridge.log("[" + TAG + "] OK VideoBean canWatch/reason/buy/type");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL VideoBean: " + t);
        }
    }

    // ============================================================
    // 5. 弹窗判定核心: VideoPopupResolver.resolve 永远返回 NONE
    // ============================================================
    private void hookPopupResolver(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> resolverClass = XposedHelpers.findClass(VIDEO_POPUP_RESOLVER, lpparam.classLoader);

            // 找 PopupType enum 和 NONE 常量
            Class<?> popupTypeClass = null;
            for (Class<?> c : resolverClass.getDeclaredClasses()) {
                if (c.isEnum() && c.getSimpleName().equals("PopupType")) {
                    popupTypeClass = c;
                    break;
                }
            }
            if (popupTypeClass == null) {
                XposedBridge.log("[" + TAG + "] FAIL PopupType enum 未找到");
                return;
            }
            final Object popupTypeNone = popupTypeClass.getEnumConstants()[0]; // NONE

            XposedHelpers.findAndHookMethod(resolverClass, "resolve",
                    XposedHelpers.findClass(BEAN_VIDEO_BEAN, lpparam.classLoader),
                    double.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return popupTypeNone;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK PopupResolver.resolve->NONE");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL PopupResolver: " + t);
        }
    }

    // ============================================================
    // 6. ShortVideoPlayActivity.showBlockingPopup 永远 false (不弹)
    // ============================================================
    private void hookShortVideoPopup(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_SHORT_VIDEO, lpparam.classLoader);
            // showBlockingPopup(VideoBean) -> boolean
            XposedHelpers.findAndHookMethod(cls, "showBlockingPopup",
                    XposedHelpers.findClass(BEAN_VIDEO_BEAN, lpparam.classLoader),
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return Boolean.FALSE;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK ShortVideoPlayActivity.showBlockingPopup->false");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL ShortVideoPopup: " + t);
        }
    }

    // ============================================================
    // 7. PhotoTikTokPlayer.videoCanWatch 强制 canWatch=true
    //    bean 字段是 PhotoVideoBean.PhotoVideoData (不是 VideoBean)
    // ============================================================
    private void hookPhotoTikTokCanWatch(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_PHOTO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "videoCanWatch", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // bean 字段是 PhotoVideoBean.PhotoVideoData
                    Object bean = XposedHelpers.getObjectField(param.thisObject, "bean");
                    if (bean != null) {
                        try {
                            XposedHelpers.callMethod(bean, "setCanWatch", true);
                            XposedBridge.log("[" + TAG + "] PhotoTikTok.bean.setCanWatch(true) 强制");
                        } catch (Throwable e) {
                            // PhotoVideoData 可能字段名不同, 静默跳过
                        }
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK PhotoTikTokPlayer.videoCanWatch");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL PhotoTikTok: " + t);
        }
    }

    // ============================================================
    // 8. BrushVideoPlayer.canWatch() 和 videoCanWatch() 强制 setCanWatch(true)
    //    canWatch() 走 HTTP GET /api/video/can/watch (用 onLvSuccess 拿 playPath)
    //    videoCanWatch() 走同一个接口但只设 canWatch
    //    两个都 hook 保险: 一个设 canWatch + playPath, 一个只设 canWatch
    // ============================================================
    private void hookBrushCanWatch(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_BRUSH, lpparam.classLoader);
            // canWatch() - 负责设置 playPath + startPlay
            XposedHelpers.findAndHookMethod(cls, "canWatch", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object videoBean = XposedHelpers.getObjectField(param.thisObject, "videoBean");
                    if (videoBean != null) {
                        XposedHelpers.callMethod(videoBean, "setCanWatch", true);
                        XposedBridge.log("[" + TAG + "] BrushVideo.canWatch->setCanWatch(true)");
                    }
                }
            });
            // videoCanWatch() - 只设 canWatch
            XposedHelpers.findAndHookMethod(cls, "videoCanWatch", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object videoBean = XposedHelpers.getObjectField(param.thisObject, "videoBean");
                    if (videoBean != null) {
                        XposedHelpers.callMethod(videoBean, "setCanWatch", true);
                        XposedBridge.log("[" + TAG + "] BrushVideo.videoCanWatch->setCanWatch(true)");
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] OK BrushVideoPlayer.canWatch/videoCanWatch");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL BrushCanWatch: " + t);
        }
    }

    // ============================================================
    // 9. AdNewUtils 全部广告接口返空
    //    - getAdWeight(String)        → null
    //    - getStartAd()               → empty list
    //    - getAdsWeight(String,int)   → empty list
    //    - getAdSort(String)          → empty list
    //    - getStationAd(int)          → null
    //    - getAppStationAd(int)       → null
    // ============================================================
    private void hookAdNewUtils(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(UTILS_AD_NEW, lpparam.classLoader);

            // 单个广告位 → null
            XposedHelpers.findAndHookMethod(cls, "getAdWeight",
                    String.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
            // 开屏广告 → 空 list
            XposedHelpers.findAndHookMethod(cls, "getStartAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return new ArrayList();
                        }
                    });
            // 多个广告位 → 空 list
            XposedHelpers.findAndHookMethod(cls, "getAdsWeight",
                    String.class, int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return new ArrayList();
                        }
                    });
            // 全列表 → 空 list
            XposedHelpers.findAndHookMethod(cls, "getAdSort",
                    String.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return new ArrayList();
                        }
                    });
            // 站位广告 → null
            XposedHelpers.findAndHookMethod(cls, "getStationAd",
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
            // app 站位广告 → null
            XposedHelpers.findAndHookMethod(cls, "getAppStationAd",
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK AdNewUtils 全部广告返空");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL AdNewUtils: " + t);
        }
    }

    // ============================================================
    // 10. VideoPlayActivity.setFullAd (LONG_VIDEO_PLAY_START 倒计时广告)
    //    void setFullAd()
    // ============================================================
    private void hookSetFullAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_VIDEO_PLAY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "setFullAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK VideoPlayActivity.setFullAd");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL setFullAd: " + t);
        }
    }

    // ============================================================
    // 11. VideoPlayActivity.setAd (INSERT_IMAGE 插入图片广告)
    //    void setAd()
    // ============================================================
    private void hookSetAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_VIDEO_PLAY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "setAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK VideoPlayActivity.setAd");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL setAd: " + t);
        }
    }

    // ============================================================
    // 12. VideoPlayer.showAdView (LONG_VIDEO_PAUSE 暂停广告)
    //    private void showAdView()
    // ============================================================
    private void hookShowAdView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(PLAYER_VIDEO, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "showAdView",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK VideoPlayer.showAdView");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL showAdView: " + t);
        }
    }

    // ============================================================
    // 13. SplashActivity 开屏广告 adShowMine
    //    void adShowMine(List<AdBaseInfoBean> list)
    // ============================================================
    private void hookSplashAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(ACT_SPLASH, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(cls, "adShowMine",
                    List.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[" + TAG + "] SplashActivity.adShowMine 拦截 (开屏6s)");
                            return null;
                        }
                    });
            XposedBridge.log("[" + TAG + "] OK SplashActivity.adShowMine");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAIL SplashAd: " + t);
        }
    }

    // ============================================================
    // 14. 跳过 - BaseApp.adFinish 是静态字段，非方法
    //   VideoPlayer.showAd() 邧断: if (secProgress<=0 || adInfoBean==null || BaseApp.adFinish) return
    //   adInfoBean 来自 VideoPlayer.init() 里的 getAdWeight("PLAY_PAGE_THUMBNAIL")
    //   → hook getAdWeight 返 null 后 adInfoBean 永远 null → showAd 永远 return
    //   → 不需要专门 hook adFinish
    // ============================================================
}