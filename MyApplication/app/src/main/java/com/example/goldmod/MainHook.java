package com.example.goldmod;

import android.app.Activity;
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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String targetPackage = "z05guu.ynyiza.ehlown.zz8xy.d1780970667328100427";
        if (!lpparam.packageName.equals(targetPackage)) {
            return;
        }

        XposedBridge.log("=== 模块进入: " + lpparam.packageName);

        // 1. Toast 提示（可选）
        showToastOnAppStart(lpparam);

        // 2. 修改金币和余额（你已经成功的部分）
        hookGoldAndBala(lpparam);

        // 3. 去掉全屏倒计时广告
        hookFullscreenAd(lpparam);
    }

    private void showToastOnAppStart(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> applicationClass = XposedHelpers.findClass("android.app.Application", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(applicationClass, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    Toast.makeText(context, "去广告模块启动", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Toast Hook 失败: " + t);
        }
    }

    private void hookGoldAndBala(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> userAccountClass = XposedHelpers.findClass("com.androidx.lv.base.bean.UserAccount", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(userAccountClass, "getGold", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(MOD_GOLD);
                    XposedBridge.log("修改 getGold -> " + MOD_GOLD);
                }
            });
            XposedHelpers.findAndHookMethod(userAccountClass, "getBala", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(MOD_BALA);
                    XposedBridge.log("修改 getBala -> " + MOD_BALA);
                }
            });
            XposedBridge.log("Hook UserAccount 成功");
        } catch (Throwable t) {
            XposedBridge.log("Hook UserAccount 失败: " + t);
        }
    }

    private void hookFullscreenAd(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass("com.grass.mh.ui.home.VideoPlayActivity", lpparam.classLoader);
            // 替换 setFullAd 方法，什么都不做
            XposedHelpers.findAndHookMethod(activityClass, "setFullAd", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("拦截 setFullAd，全屏广告已跳过");
                    // 直接返回，不显示广告
                    return null;
                }
            });
            XposedBridge.log("Hook setFullAd 成功");
        } catch (Throwable t) {
            XposedBridge.log("Hook setFullAd 失败: " + t);
        }
    }
}