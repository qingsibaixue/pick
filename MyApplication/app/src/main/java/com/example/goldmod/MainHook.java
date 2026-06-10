package com.example.goldmod;

import android.content.Context;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    static {
        android.util.Log.d("GoldMod", "===== MainHook 类被加载了！ =====");
    }
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只处理目标应用
        String targetPackage = "z05guu.ynyiza.ehlown.zz8xy.d1780970667328100427";
        if (!lpparam.packageName.equals(targetPackage)) {
            return;
        }

        XposedBridge.log("进入目标应用: " + lpparam.packageName);

        // 方法一：通过 Hook Application.attach 获取 Context 并弹 Toast
        Class<?> applicationClass = XposedHelpers.findClass("android.app.Application", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(applicationClass, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                Toast.makeText(context, "GoldMod 模块已加载！", Toast.LENGTH_LONG).show();
                XposedBridge.log("Toast 已显示，模块激活成功");
            }
        });

        // 方法二：Hook SpUtils.getUserAccount 修改金币
        try {
            Class<?> spUtilsClass = XposedHelpers.findClass("com.androidx.lv.base.utils.SpUtils", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(spUtilsClass, "getUserAccount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object userAccount = param.getResult();
                    if (userAccount != null) {
                        // 修改 gold 和 bala
                        XposedHelpers.callMethod(userAccount, "setGold", 999999999.0);
                        XposedHelpers.callMethod(userAccount, "setBala", 888888888.0);
                        XposedBridge.log("已将 gold 和 bala 修改为最大值");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Hook getUserAccount 失败: " + t.getMessage());
        }
    }
}