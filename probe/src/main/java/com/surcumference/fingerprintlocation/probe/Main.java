package com.surcumference.fingerprintlocation.probe;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import com.surcumference.fingerprintlocation.FingerprintLocation;
import com.surcumference.fingerprintlocation.core.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only app_process diagnostic. Does not start authentication or request root. */
public final class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("FINGERPRINT_LOCATION_STARTED");
        System.out.flush();
        if(android.os.Looper.myLooper()==null)android.os.Looper.prepareMainLooper();
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        java.lang.reflect.Constructor<?> constructor=activityThread.getDeclaredConstructor();constructor.setAccessible(true);
        Object thread=constructor.newInstance();
        java.lang.reflect.Field current=activityThread.getDeclaredField("sCurrentActivityThread");current.setAccessible(true);current.set(null,thread);
        if(android.os.Build.VERSION.SDK_INT>=31) {
            Class<?> controllerClass=Class.forName("android.app.ConfigurationController");
            java.lang.reflect.Constructor<?> controllerConstructor=controllerClass.getDeclaredConstructors()[0];
            controllerConstructor.setAccessible(true);
            Object controller=controllerConstructor.newInstance(thread);
            java.lang.reflect.Field controllerField=activityThread.getDeclaredField("mConfigurationController");
            controllerField.setAccessible(true);controllerField.set(thread,controller);
        }
        Context context=(Context)activityThread.getMethod("getSystemContext").invoke(thread);
        DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        JSONArray displays=new JSONArray();
        for(Display display:manager.getDisplays()) {
            Geometry.DisplayFrame frame=FingerprintLocation.displayFrame(display);
            Resolution result=FingerprintLocation.query(context,display);
            JSONObject entry=new JSONObject().put("displayId",frame.displayId).put("width",frame.logicalWidth())
                    .put("height",frame.logicalHeight()).put("rotation",frame.rotation).put("status",result.status.name())
                    .put("corroborated",result.corroborated);
            entry.put("sensorType", result.sensorType.name()).put("typeStatus", result.typeStatus.name());
            JSONArray typeEvidence = new JSONArray();
            for (Observation evidence : result.typeEvidence)
                typeEvidence.put(new JSONObject().put("source", evidence.source.name()).put("type", evidence.type.name())
                        .put("sensorId", evidence.sensorId).put("display", evidence.displayId).put("evidence", evidence.evidence));
            entry.put("typeEvidence", typeEvidence);
            if(result.bounds!=null)entry.put("bounds",bounds(result.bounds)).put("source",result.selected.source.name());
            JSONArray observations=new JSONArray();
            for(Observation observation:result.observations) {
                JSONObject value=new JSONObject().put("source",observation.source.name()).put("type",observation.type.name())
                        .put("sensorId",observation.sensorId).put("display",observation.displayId)
                        .put("space",observation.space.name()).put("evidence",observation.evidence)
                        .put("frameworkPlaceholder",observation.frameworkPlaceholder);
                if(observation.bounds!=null)value.put("rawBounds",bounds(observation.bounds));
                observations.put(value);
            }
            JSONArray issues=new JSONArray();
            for(Resolution.Issue issue:result.issues)issues.put(new JSONObject().put("provider",issue.provider).put("code",issue.code.name()).put("exceptionType",issue.exceptionType));
            entry.put("observations",observations).put("issues",issues);displays.put(entry);
        }
        System.out.println("FINGERPRINT_LOCATION_JSON="+new JSONObject().put("schema",1).put("displays",displays));
        System.out.flush();
        System.exit(0);
    }
    private static JSONArray bounds(Geometry.Bounds value) throws org.json.JSONException {
        return new JSONArray().put(value.left).put(value.top).put(value.right).put(value.bottom);
    }
}
