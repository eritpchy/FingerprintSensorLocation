package org.ifaa.android.manager;

import android.content.Context;

public final class IFAAManagerFactory {
    public static Object manager;
    public static Object getIFAAManager(Context context,int type) {
        if(type!=1)throw new AssertionError("Location query must use fingerprint auth type");
        return manager;
    }
}
