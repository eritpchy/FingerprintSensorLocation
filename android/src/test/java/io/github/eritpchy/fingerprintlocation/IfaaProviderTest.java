package io.github.eritpchy.fingerprintlocation;

import io.github.eritpchy.fingerprintlocation.core.*;
import org.ifaa.android.manager.IFAAManagerFactory;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={31,36})
public class IfaaProviderTest {
    public static class Manager {
        final String answer;
        int calls;
        Manager(String answer){this.answer=answer;}
        public String getExtInfo(int authType,String key) {
            assertEquals(1,authType);assertEquals("org.ifaa.ext.key.GET_SENSOR_LOCATION",key);calls++;return answer;
        }
    }
    @After public void clear(){IFAAManagerFactory.manager=null;}
    private Resolution query(String answer,int rotation) {
        IFAAManagerFactory.manager=new Manager(answer);
        Geometry.DisplayFrame frame=new Geometry.DisplayFrame("local:1",0,1080,2400,1440,3200,rotation);
        Probe probe=new Probe();AndroidProviders.ifaa(RuntimeEnvironment.getApplication(),frame,-1,probe);return probe.resolve(frame,-1);
    }
    @Test public void ifaaIsDisabledByDefault(){assertFalse(new FingerprintLocation.Options.Builder().build().includeIfaa);}
    @Test public void currentResolutionBoundsAreRotatedWithoutNativeScaling() {
        Resolution r=query("{\"type\":0,\"fullView\":{\"startX\":450,\"startY\":2010,\"width\":180,\"height\":180,\"unit\":\"px\"}}",1);
        assertEquals(Resolution.Status.FOUND,r.status);assertEquals(2100,r.bounds.centerX(),0);assertEquals(540,r.bounds.centerY(),0);
        assertEquals(1,((Manager)IFAAManagerFactory.manager).calls);
    }
    @Test public void malformedOrWrongUnitOrUnknownSchemaIsNotAUsableLocation() {
        for(String answer:new String[]{"", "{}", "{\"type\":1,\"fullView\":{}}",
                "{\"type\":0,\"fullView\":{\"startX\":450,\"startY\":2010,\"width\":180,\"height\":180,\"unit\":\"dp\"}}",
                "{\"type\":0,\"fullView\":{\"startX\":450,\"startY\":2010,\"width\":-1,\"height\":180}}"}) {
            Resolution r=query(answer,0);assertNotEquals(answer,Resolution.Status.FOUND,r.status);assertNull(r.bounds);
            assertTrue(r.issues.stream().anyMatch(i->i.code==Resolution.Issue.Code.INVALID_DATA));
        }
    }
    @Test public void noIfaaManagerIsAnExplicitUnavailableResult() {
        Probe p=new Probe();AndroidProviders.ifaa(RuntimeEnvironment.getApplication(),
                new Geometry.DisplayFrame("",0,1080,2400,1080,2400,0),-1,p);
        assertEquals(Resolution.Status.UNAVAILABLE,p.resolve(new Geometry.DisplayFrame("",0,1080,2400,1080,2400,0),-1).status);
    }
}
