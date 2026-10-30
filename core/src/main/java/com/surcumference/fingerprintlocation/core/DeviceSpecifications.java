package com.surcumference.fingerprintlocation.core;

/** Optional, exact-model placement declarations. Never supplies screen geometry. */
public final class DeviceSpecifications {
    private DeviceSpecifications() {}

    public static void collect(String manufacturer, String model, Geometry.DisplayFrame display,
                               int sensorId, boolean fingerprintFeature, Probe probe) {
        if (display.displayId != 0 || sensorId >= 0 || !fingerprintFeature) return;
        Resolution runtime = probe.resolve(display, sensorId);
        if (runtime.typeStatus != Resolution.TypeStatus.UNKNOWN
                || runtime.status != Resolution.Status.UNAVAILABLE && runtime.status != Resolution.Status.ACCESS_DENIED) return;
        Observation.Type type = null;
        String evidence = null;
        String verified = "2026-10-26";
        if ("Google".equalsIgnoreCase(manufacturer) && "Pixel 8 Pro".equals(model)) {
            type = Observation.Type.UNDER_DISPLAY;
            evidence = "https://support.google.com/pixelphone/answer/7158570?hl=en";
        } else if ("samsung".equalsIgnoreCase(manufacturer) && "SM-A315N".equals(model)) {
            type = Observation.Type.OPTICAL;
            evidence = "https://www.samsungsvc.co.kr/solution/25853";
        } else if ("samsung".equalsIgnoreCase(manufacturer) && "SM-S928U1".equals(model)) {
            type = Observation.Type.ULTRASONIC;
            evidence = "https://images.samsung.com/is/content/samsung/assets/us/2401/business/mobile/phones/galaxy/Galaxy_S24_Series_B2B_Spec_Sheet.pdf";
        } else if ("vivo".equalsIgnoreCase(manufacturer) && "vivo X20Plus A".equals(model)) {
            type = Observation.Type.REAR;
            evidence = "https://www.vivo.com.cn/vivo/x20plus/";
        } else if ("vivo".equalsIgnoreCase(manufacturer) && "V2158A".equals(model)) {
            type = Observation.Type.SIDE;
            evidence = "https://www.vivo.com.cn/vivo/param/y32";
            verified = "2026-10-27";
        } else if ("vivo".equalsIgnoreCase(manufacturer) && "V1831A".equals(model)) {
            type = Observation.Type.REAR;
            evidence = "https://m.vivo.com.cn/vivo/param/s1";
            verified = "2026-10-30";
        } else if ("OPPO".equalsIgnoreCase(manufacturer) && "PEQM00".equals(model)) {
            type = Observation.Type.UNDER_DISPLAY;
            evidence = "https://www.oppo.com/cn/smartphones/series-reno/reno6/specs/";
            verified = "2026-10-28";
        } else if ("OPPO".equalsIgnoreCase(manufacturer) && "PFVM10".equals(model)) {
            type = Observation.Type.SIDE;
            evidence = "https://www.oppo.com/cn/smartphones/series-a/a56/specs/";
            verified = "2026-10-28";
        } else if ("Xiaomi".equalsIgnoreCase(manufacturer) && "23127PN0CG".equals(model)) {
            type = Observation.Type.UNDER_DISPLAY;
            evidence = "https://www.mi.com/sg/product/xiaomi-14/specs/";
        } else if ("HUAWEI".equalsIgnoreCase(manufacturer) && "VOG-L29".equals(model)) {
            type = Observation.Type.UNDER_DISPLAY;
            evidence = "https://consumer.huawei.com/se/phones/p30-pro-back-up/specs/";
        } else if ("Nothing".equalsIgnoreCase(manufacturer) && "A069".equals(model)) {
            type = Observation.Type.OPTICAL;
            evidence = "https://nothing.tech/products/phone-4a";
        } else if ("HONOR".equalsIgnoreCase(manufacturer) && "BVL-N49".equals(model)) {
            type = Observation.Type.UNDER_DISPLAY;
            evidence = "https://www.honor.com/content/dam/honor/pl/support/product-manual/honor-magic6-pro-ug.pdf";
            verified = "2026-10-27";
        } else if ("SHARP".equalsIgnoreCase(manufacturer) && "SH-01L".equals(model)) {
            type = Observation.Type.FRONT;
            evidence = "https://jp.sharp/products/sh01l/usability.html";
            verified = "2026-10-30";
        } else if ("TECNO".equalsIgnoreCase(manufacturer) && "TECNO KI5k".equals(model)) {
            type = Observation.Type.SIDE;
            evidence = "https://device.report/m/03a3cb22f3a0b30d66c3f2ab2a4eea5bb22c2f2d0865b64d01baf8ace389680a_optim.pdf";
            verified = "2026-10-30";
        } else if ("Sony".equalsIgnoreCase(manufacturer) && "A402SO".equals(model)) {
            type = Observation.Type.SIDE;
            evidence = "https://www.softbank.jp/mobile/support/manual/smartphone/xperia10m6/detail/158378/";
            verified = "2026-10-29";
        }
        if (type != null) probe.add(new Observation(-1, "", type, Observation.Source.DEVICE_SPECIFICATION,
                Geometry.Space.NATIVE_NATURAL, null, evidence + " verified " + verified));
    }
}
