package com.surcumference.fingerprintlocation;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import com.surcumference.fingerprintlocation.core.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={23,31,36})
public class VivoServiceTest {
    static final String MODERN="com.vivo.fingerprintui";
    static final String LEGACY="com.vivo.udfingerprint";
    static final String ANSWER="{\"version_major\":1,\"version_minor\":1,\"has_under_display_fingerprint\":true,\"icon_state\":-1,\"icon_position\":[519,2422,741,2644]}";
    final Geometry.DisplayFrame frame=new Geometry.DisplayFrame("local:1",0,1260,2800,1440,3200,0);
    final ExecutorService executor=Executors.newSingleThreadExecutor();
    Context base;
    TestContext context;
    ServiceInfo service;
    @Before public void setup() throws Exception {
        base=RuntimeEnvironment.getApplication();
        PackageInfo own=base.getPackageManager().getPackageInfo(base.getPackageName(),0);
        own.applicationInfo.uid=android.os.Process.myUid();Shadows.shadowOf(base.getPackageManager()).installPackage(own);
        service=new ServiceInfo();service.name=MODERN+".export.MessengerService";service.packageName=MODERN;
        service.exported=true;service.enabled=false;service.applicationInfo=new ApplicationInfo();
        service.applicationInfo.packageName=MODERN;service.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;service.applicationInfo.uid=1001;
        install();context=new TestContext(base);
    }
    void install(){PackageInfo pkg=new PackageInfo();pkg.packageName=MODERN;pkg.applicationInfo=service.applicationInfo;pkg.services=new ServiceInfo[]{service};Shadows.shadowOf(base.getPackageManager()).installPackage(pkg);}
    @After public void stop(){executor.shutdownNow();Shadows.shadowOf(Looper.getMainLooper()).idle();}
    final class TestContext extends ContextWrapper {
        final CountDownLatch attempted=new CountDownLatch(1);
        final List<Integer> messages=new ArrayList<>();
        int binds,unbinds,permission=PackageManager.PERMISSION_GRANTED,expectedMinor=1;
        boolean bindResult=true,respond=true,denied,disconnected,nullBinding;
        String expectedPackage=MODERN;
        String answer=ANSWER;
        Messenger client;
        ServiceConnection connection;
        TestContext(Context base){super(base);}
        @Override public int checkSelfPermission(String ignored){return permission;}
        @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags) {
            binds++;this.connection=connection;
            try {
                assertEquals(Context.BIND_AUTO_CREATE,flags);assertEquals(1,intent.getIntExtra("version_major",-1));
                assertEquals(expectedMinor,intent.getIntExtra("version_minor",-1));
                assertEquals(expectedPackage,intent.getComponent().getPackageName());
                if(denied)throw new SecurityException("private fixture message");
                if(!bindResult)return false;
                if(disconnected){connection.onServiceDisconnected(intent.getComponent());return true;}
                if(nullBinding){connection.onNullBinding(intent.getComponent());return true;}
                Messenger server=new Messenger(new Handler(Looper.getMainLooper(),message->{
                    messages.add(message.what);assertEquals(getPackageName(),message.getData().getString("token"));assertNotNull(message.replyTo);
                    if(message.what==10000) {
                        client=message.replyTo;
                        if(respond)try {Message reply=Message.obtain(null,10000);Bundle data=new Bundle();data.putString("query_json",answer);reply.setData(data);client.send(reply);}catch(RemoteException failure){throw new AssertionError(failure);}
                    }
                    return true;
                }));
                connection.onServiceConnected(intent.getComponent(),server.getBinder());return true;
            } finally {attempted.countDown();}
        }
        @Override public void unbindService(ServiceConnection connection){assertSame(this.connection,connection);unbinds++;}
    }
    Probe collect(int timeout) throws Exception {
        Future<Probe> future=executor.submit(()->{Probe p=new Probe();VivoService.collect(context,frame,-1,timeout,p);return p;});
        assertTrue(context.attempted.await(1,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle();
        Probe probe=future.get(3,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();return probe;
    }
    @Test public void genuineCallerReadsConfiguredRegionAndAlwaysUnsubscribes() throws Exception {
        Probe probe=collect(1000);Resolution r=probe.resolve(frame,-1);
        assertEquals(Resolution.Status.FOUND,r.status);assertEquals(Observation.Type.UNDER_DISPLAY,r.sensorType);
        assertEquals(Observation.Source.VIVO_SERVICE,r.selected.source);assertEquals(519,r.bounds.left,0);assertEquals(2644,r.bounds.bottom,0);
        assertEquals(Arrays.asList(10000,10002),context.messages);assertEquals(1,context.unbinds);
        Geometry.DisplayFrame rotated=new Geometry.DisplayFrame("local:1",0,1260,2800,1440,3200,1);
        Resolution landscape=probe.resolve(rotated,-1);assertEquals(2422,landscape.bounds.left,0);assertEquals(519,landscape.bounds.top,0);
        assertEquals(2644,landscape.bounds.right,0);assertEquals(741,landscape.bounds.bottom,0);
    }
    @Test public void falseBindStillReleasesItsDispatcher() throws Exception {
        context.bindResult=false;Probe p=collect(1000);assertEquals(1,context.unbinds);assertTrue(context.messages.isEmpty());assertTrue(p.observations().isEmpty());
    }
    void installLegacy() {
        ServiceInfo legacy=new ServiceInfo();legacy.name=LEGACY+".service.MessengerService";legacy.packageName=LEGACY;
        legacy.exported=true;legacy.applicationInfo=new ApplicationInfo();legacy.applicationInfo.packageName=LEGACY;
        legacy.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;legacy.applicationInfo.uid=1002;
        PackageInfo pkg=new PackageInfo();pkg.packageName=LEGACY;pkg.applicationInfo=legacy.applicationInfo;
        pkg.services=new ServiceInfo[]{legacy};Shadows.shadowOf(base.getPackageManager()).installPackage(pkg);
    }
    @Test public void legacyEndpointUsesVersion10AndGenuineIdentity() throws Exception {
        service.exported=false;install();installLegacy();context.expectedMinor=0;context.expectedPackage=LEGACY;
        context.answer=ANSWER.replace("\"version_minor\":1","\"version_minor\":0");
        Resolution r=collect(1000).resolve(frame,-1);assertEquals(Resolution.Status.FOUND,r.status);
        assertTrue(r.selected.evidence.contains("com.vivo.udfingerprint/"));assertTrue(r.selected.evidence.endsWith("v1.0"));
        assertEquals(Arrays.asList(10000,10002),context.messages);assertEquals(1,context.unbinds);
    }
    @Test public void disconnectedAndNullBindingsFinishAndReleaseWithoutGeometry() throws Exception {
        for(boolean disconnected:new boolean[]{true,false}) {
            context=new TestContext(base);context.disconnected=disconnected;context.nullBinding=!disconnected;
            Probe p=collect(1000);assertTrue(p.observations().isEmpty());assertEquals(1,context.unbinds);
            assertTrue(context.messages.isEmpty());assertEquals(Resolution.TypeStatus.UNKNOWN,p.resolve(frame,-1).typeStatus);
        }
    }
    @Test public void interruptDuringCallbackWaitReleasesAndPreservesInterrupt() throws Exception {
        context.respond=false;java.util.concurrent.atomic.AtomicReference<Thread> worker=new java.util.concurrent.atomic.AtomicReference<>();
        Future<Boolean> future=executor.submit(()->{worker.set(Thread.currentThread());Probe p=new Probe();
            VivoService.collect(context,frame,-1,1000,p);return Thread.currentThread().isInterrupted()
                    &&p.observations().isEmpty()&&p.issues().stream().anyMatch(i->i.exceptionType.equals("java.lang.InterruptedException"));});
        assertTrue(context.attempted.await(1,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle();
        worker.get().interrupt();assertTrue(future.get(2,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Arrays.asList(10000,10002),context.messages);assertEquals(1,context.unbinds);
    }
    @Test public void timeoutBudgetIsSharedAcrossBothInstalledEndpoints() throws Exception {
        installLegacy();context.respond=false;
        if(Build.VERSION.SDK_INT<28){context.expectedMinor=0;context.expectedPackage=LEGACY;}
        Probe p=collect(100);assertEquals(1,context.binds);assertEquals(1,context.unbinds);assertTrue(p.observations().isEmpty());
        assertTrue(p.issues().stream().anyMatch(i->i.code==Resolution.Issue.Code.TIMEOUT));
    }
    @Test public void timeoutUnbindsAndRejectsLateReplies() throws Exception {
        context.respond=false;Probe p=collect(100);
        assertTrue(p.issues().stream().anyMatch(i->i.code==Resolution.Issue.Code.TIMEOUT));assertEquals(1,context.unbinds);
        Message late=Message.obtain(null,10000);Bundle data=new Bundle();data.putString("query_json",ANSWER);late.setData(data);context.client.send(late);
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(p.observations().isEmpty());assertEquals(Arrays.asList(10000,10002),context.messages);
    }
    @Test public void bindSecurityFailureIsExplicitAndReleasesAttemptedDispatcher() throws Exception {
        context.denied=true;Probe p=collect(1000);assertEquals(Resolution.Status.ACCESS_DENIED,p.resolve(frame,-1).status);assertEquals(1,context.unbinds);
        assertTrue(p.issues().stream().anyMatch(i->i.exceptionType.equals("java.lang.SecurityException")));
    }
    @Test public void mainThreadAndForeignDisplayNeverBind() {
        Probe p=new Probe();VivoService.collect(context,frame,-1,100,p);
        assertTrue(p.issues().stream().anyMatch(i->i.code==Resolution.Issue.Code.WRONG_THREAD));
        VivoService.collect(context,new Geometry.DisplayFrame("local:2",1,1260,2800,1260,2800,0),-1,100,p);assertEquals(0,context.binds);
    }
    @Test public void nonSystemOwnerAndProtectedServiceAreRejectedBeforeBinding() throws Exception {
        service.applicationInfo.flags=0;install();Future<Probe> a=executor.submit(()->{Probe p=new Probe();VivoService.collect(context,frame,-1,100,p);return p;});
        assertTrue(a.get(1,TimeUnit.SECONDS).issues().stream().anyMatch(i->i.code==Resolution.Issue.Code.UNTRUSTED_SOURCE));assertEquals(0,context.binds);
        service.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;service.permission="fixture.permission";install();context.permission=PackageManager.PERMISSION_DENIED;
        Future<Probe> b=executor.submit(()->{Probe p=new Probe();VivoService.collect(context,frame,-1,100,p);return p;});assertEquals(Resolution.Status.ACCESS_DENIED,b.get(1,TimeUnit.SECONDS).resolve(frame,-1).status);assertEquals(0,context.binds);
    }
    @Test public void foreignPackageContextNeverImpersonatesTheCaller() throws Exception {
        Context foreign=new ContextWrapper(context){@Override public String getPackageName(){return "com.tencent.mm";}};
        Future<Probe> future=executor.submit(()->{Probe p=new Probe();VivoService.collect(foreign,frame,-1,100,p);return p;});
        assertEquals(Resolution.Status.ACCESS_DENIED,future.get(1,TimeUnit.SECONDS).resolve(frame,-1).status);assertEquals(0,context.binds);
    }
    @Test public void interruptBeforeBindIsPreserved() throws Exception {
        Future<Boolean> future=executor.submit(()->{Thread.currentThread().interrupt();Probe p=new Probe();VivoService.collect(context,frame,-1,100,p);return Thread.currentThread().isInterrupted()&&p.issues().stream().anyMatch(i->i.exceptionType.equals("java.lang.InterruptedException"));});
        assertTrue(future.get(1,TimeUnit.SECONDS));assertEquals(0,context.binds);
    }
    @Test public void versionsAndTypesAreStrictButInvalidGeometryPreservesKnownPlacement() {
        for(String raw:new String[]{"{}",ANSWER.replace("\"version_major\":1","\"version_major\":2"),ANSWER.replace("\"version_minor\":1","\"version_minor\":9"),ANSWER.replace("\"has_under_display_fingerprint\":true","\"has_under_display_fingerprint\":\"true\"")}) {
            Probe p=new Probe();VivoService.parse(raw,"fixture",-1,p);assertEquals(Observation.Type.UNKNOWN,p.resolve(frame,-1).sensorType);assertNull(p.resolve(frame,-1).bounds);
        }
        for(String raw:new String[]{ANSWER.replace("519,2422,741,2644","519,2422,741"),ANSWER.replace("519,2422,741,2644","519.5,2422,741,2644"),ANSWER.replace("519,2422,741,2644","519,2422,519,2644"),ANSWER.replace("519,2422,741,2644","519,2422,741,9999")}) {
            Probe p=new Probe();VivoService.parse(raw,"fixture",-1,p);Resolution r=p.resolve(frame,-1);assertEquals(Observation.Type.UNDER_DISPLAY,r.sensorType);assertNull(r.bounds);assertEquals(Resolution.Status.UNAVAILABLE,r.status);
        }
    }
    @Test public void explicitNonDisplayDeclarationDoesNotInventSideOrRearOrBounds() {
        Probe p=new Probe();VivoService.parse(ANSWER.replace("\"has_under_display_fingerprint\":true","\"has_under_display_fingerprint\":false"),"fixture",-1,p);
        Resolution r=p.resolve(frame,-1);assertEquals(Observation.Type.NON_UNDER_DISPLAY,r.sensorType);assertEquals(Resolution.Status.NOT_UNDER_DISPLAY,r.status);assertNull(r.bounds);
        assertEquals(Observation.Type.UNKNOWN,p.resolve(frame,7).sensorType);
    }
    @Test public void serviceCanBeDisabledAndTimeoutRangeIsValidated() {
        assertTrue(new FingerprintLocation.Options.Builder().build().includeVivoService);
        assertFalse(new FingerprintLocation.Options.Builder().includeVivoService(false).build().includeVivoService);
        assertThrows(IllegalArgumentException.class,()->new FingerprintLocation.Options.Builder().vivoServiceTimeoutMillis(0));
        assertThrows(IllegalArgumentException.class,()->new FingerprintLocation.Options.Builder().vivoServiceTimeoutMillis(5001));
    }
}
