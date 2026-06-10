package com.example.goldmod;

import android.content.Context;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final double MOD_GOLD = 199999.0;
    private static final double MOD_BALA = 99999.0;

    // 反编译 AndroidManifest.xml 验证过的目标包名
    private static final String TARGET_PACKAGE = "z05guu.ynyiza.ehlown.zz8xy.d1780970667328100427";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log("=== [GOLD] 模块进入: " + lpparam.packageName + " classLoader=" + lpparam.classLoader);

        // 0. Toast 提示
        hookToastOnAppStart(lpparam);

        // 1. ⭐ 核心: 取消所有弹窗 - 拦截 VideoPopupResolver.resolve
        //    反编译: sources/com/grass/mh/video/VideoPopupResolver.java:30
        //    dex: classes2.dex
        //    该方法被 VideoPlayActivity + ShortVideoPlayActivity 共同调用,
        //    是 4 种弹窗 (FREE_LIMIT/VIP_REQUIRED/GOLD_CONFIRM/GOLD_NOT_ENOUGH) 的唯一入口
        //    hook 后永远返回 NONE -> 4 种弹窗全消失
        hookPopupResolver(lpparam);

        // 2. 开会员 - 让 UserInfo.isVIP() 永远返回 true
        //    反编译: sources/com/androidx/lv/base/bean/UserInfo.java:564
        //    dex: classes.dex
        //    原代码: return this.vipType >= 1;
        //    副作用: setFullAd 早返; initPopAdDialog 早返; playVideo 的免费次数限制跳过
        hookUserInfoIsVip(lpparam);

        // 3. 保留: 金币/余额 (反编译 UserAccount.java classes.dex)
        hookGoldAndBala(lpparam);

        // 4. 保留: 全屏倒计时广告 (反编译 VideoPlayActivity.java:623 classes2.dex)
        hookFullscreenAd(lpparam);

        // 5. ⚠️ 新增: 插入式图片广告 setAd() (反编译 VideoPlayActivity.java:958 classes2.dex)
        //    关键: setAd() 没有 isVIP 早返, 必须单独 hook
        hookInsertImageAd(lpparam);

        XposedBridge.log("=== [GOLD] 所有 hook 注册完毕");
    }

    // ================== Hook 实现 ==================

    private void hookToastOnAppStart(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> applicationClass = XposedHelpers.findClass(
                    "android.app.Application", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(applicationClass, "attach",
                    Context.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            Toast.makeText(context, "去广告模块已启动", Toast.LENGTH_SHORT).show();
                        }
                    });
            XposedBridge.log("[OK] Toast hook");
        } catch (Throwable t) {
            XposedBridge.log("[FAIL] Toast hook: " + t);
        }
    }

    /**
     * 核心 hook: 拦截弹窗判定, 永远返回 NONE
     *
     * 反编译证据 (sources/com/grass/mh/video/VideoPopupResolver.java):
     *   public static PopupType resolve(VideoBean videoBean, double goldBalance)
     *   dex: classes2.dex
     *   内部枚举 PopupType { NONE, FREE_LIMIT, VIP_REQUIRED, GOLD_CONFIRM, GOLD_NOT_ENOUGH }
     *
     * 调用方证据:
     *   - VideoPlayActivity.java:716  (long video)
     *   - ShortVideoPlayActivity.java:443  (short video)
     */
    private void hookPopupResolver(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> resolverClass = XposedHelpers.findClass(
                    "com.grass.mh.video.VideoPopupResolver", lpparam.classLoader);
            XposedBridge.log("[DEBUG] resolverClass=" + resolverClass + " loader=" + resolverClass.getClassLoader());

            // 找 PopupType 内部枚举类 - 用 getDeclaredClasses
            Class<?> popupTypeClass = null;
            for (Class<?> c : resolverClass.getDeclaredClasses()) {
                XposedBridge.log("[DEBUG] inner class: " + c.getName() + " isEnum=" + c.isEnum());
                if (c.isEnum() && c.getSimpleName().equals("PopupType")) {
                    popupTypeClass = c;
                    break;
                }
            }
            if (popupTypeClass == null) {
                throw new RuntimeException("PopupType enum not found in VideoPopupResolver");
            }
            XposedBridge.log("[DEBUG] popupTypeClass=" + popupTypeClass.getName());

            // 拿到 NONE 常量 (getEnumConstants()[0] 直接是反编译证据里的第一个)
            Object[] enumConstants = popupTypeClass.getEnumConstants();
            if (enumConstants == null || enumConstants.length == 0) {
                throw new RuntimeException("PopupType enum has no constants");
            }
            final Object popupTypeNone = enumConstants[0];
            XposedBridge.log("[DEBUG] popupTypeNone=" + popupTypeNone);

            // 加载 VideoBean 类 (resolve 方法第一个参数)
            Class<?> videoBeanClass = XposedHelpers.findClass(
                    "com.androidx.lv.base.bean.VideoBean", lpparam.classLoader);
            XposedBridge.log("[DEBUG] videoBeanClass=" + videoBeanClass);

            // 真正 hook
            XposedHelpers.findAndHookMethod(resolverClass, "resolve",
                    videoBeanClass,        // VideoBean
                    double.class,          // goldBalance
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[弹窗] resolve -> NONE (拦截成功)");
                            return popupTypeNone;
                        }
                    });
            XposedBridge.log("[OK] VideoPopupResolver.resolve hook");
        } catch (Throwable t) {
            XposedBridge.log("[FAIL] VideoPopupResolver hook: " + t);
        }
    }

    /**
     * 辅助 hook: 开会员
     * 反编译: sources/com/androidx/lv/base/bean/UserInfo.java:564
     * dex: classes.dex
     * 原: return this.vipType >= 1;
     */
    private void hookUserInfoIsVip(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> userInfoClass = XposedHelpers.findClass(
                    "com.androidx.lv.base.bean.UserInfo", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(userInfoClass, "isVIP",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return Boolean.TRUE;
                        }
                    });
            XposedBridge.log("[OK] UserInfo.isVIP hook -> true");
        } catch (Throwable t) {
            XposedBridge.log("[FAIL] UserInfo.isVIP hook: " + t);
        }
    }

    /**
     * 保留: 金币和余额
     * 反编译: sources/com/androidx/lv/base/bean/UserAccount.java
     * dex: classes.dex
     */
    private void hookGoldAndBala(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> userAccountClass = XposedHelpers.findClass(
                    "com.androidx.lv.base.bean.UserAccount", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(userAccountClass, "getGold",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(MOD_GOLD);
                        }
                    });
            XposedHelpers.findAndHookMethod(userAccountClass, "getBala",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(MOD_BALA);
                        }
                    });
            XposedBridge.log("[OK] UserAccount.getGold/getBala hook");
        } catch (Throwable t) {
            XposedBridge.log("[FAIL] UserAccount hook: " + t);
        }
    }

    /**
     * 保留: 全屏倒计时广告
     * 反编译: sources/com/grass/mh/ui/home/VideoPlayActivity.java:623
     * dex: classes2.dex
     * 注意: setFullAd 第 624 行有 isVIP 早返, 配合 hookUserInfoIsVip 双保险
     */
    private void hookFullscreenAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(
                    "com.grass.mh.ui.home.VideoPlayActivity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(activityClass, "setFullAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[广告] setFullAd 被拦截");
                            return null;
                        }
                    });
            XposedBridge.log("[OK] setFullAd hook");
        } catch (Throwable t) {
            XposedBridge.log("[FAIL] setFullAd hook: " + t);
        }
    }

    /**
     * ⚠️ 新增: 插入式图片广告 setAd()
     * 反编译: sources/com/grass/mh/ui/home/VideoPlayActivity.java:958
     * dex: classes2.dex
     *
     * 关键: 该方法没有 isVIP 早返! 必须单独 hook
     * 原代码: void setAd() -> 加载 "INSERT_IMAGE" 类型的图片广告到 adView
     */
    private void hookInsertImageAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(
                    "com.grass.mh.ui.home.VideoPlayActivity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(activityClass, "setAd",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[广告] setAd (插入式) 被拦截");
                            return null;
                        }
                    });
            XposedBridge.log("[OK] setAd hook");
        } catch (Throwable t) {
            XposedBridge.log("[FAIL] setAd hook: " + t);
        }
    }
}
