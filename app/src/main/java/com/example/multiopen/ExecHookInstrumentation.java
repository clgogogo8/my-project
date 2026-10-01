package com.example.multiopen;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 用 Java 写是因为 execStartActivity 是隐藏 API，不在 SDK stub 里，Kotlin 的 override 关键字无法编译；
 * Java 不写 @Override 就能编译，运行时依然会正确覆盖（Android 9–14 签名一致）。
 * Activity.startActivityForResult 与 Context.startActivity 走第一个重载，Fragment 走第二个。
 */
public class ExecHookInstrumentation extends HookInstrumentation {
    public ExecHookInstrumentation(Context ctx, Instrumentation base) {
        super(ctx, base);
    }

    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token,
                                            Activity target, Intent intent, int requestCode, Bundle options) {
        return callBase(new Class<?>[]{Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class},
                who, contextThread, token, target, rewriteIntent(who, intent), requestCode, options);
    }

    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token,
                                            String target, Intent intent, int requestCode, Bundle options) {
        return callBase(new Class<?>[]{Context.class, IBinder.class, IBinder.class, String.class, Intent.class, int.class, Bundle.class},
                who, contextThread, token, target, rewriteIntent(who, intent), requestCode, options);
    }

    private ActivityResult callBase(Class<?>[] types, Object... args) {
        try {
            Method m = Instrumentation.class.getDeclaredMethod("execStartActivity", types);
            m.setAccessible(true);
            return (ActivityResult) m.invoke(getBase(), args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new RuntimeException(c);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
