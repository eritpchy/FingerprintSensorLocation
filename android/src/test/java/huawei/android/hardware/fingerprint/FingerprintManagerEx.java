package huawei.android.hardware.fingerprint;

public final class FingerprintManagerEx {
    public static boolean screen;
    public static int type = 1;
    public static boolean denied;
    public static int[] spotPosition;
    public static int[] legacyPosition = {450,2000,630,2180};
    public static boolean hasFingerprintInScreen() {return screen;}
    public static int getHardwareType() {
        if (denied) throw new SecurityException("Synthetic denial");
        return type;
    }
    public static int[] getHardwarePosition() {return legacyPosition;}
    public static int[] getSpotPosition() throws NoSuchMethodException {
        if (spotPosition == null) throw new NoSuchMethodException("Synthetic legacy framework");
        return spotPosition;
    }
}
