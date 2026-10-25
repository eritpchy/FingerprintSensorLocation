package io.github.eritpchy.fingerprintlocation.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Status.*;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Issue.Code.WRONG_DISPLAY;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Issue.Code.UNBOUND_SENSOR;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Issue.Code.INVALID_DATA;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Issue.Code.UNCONFIRMED_DEFAULT;

public final class Resolver {
    private Resolver() {}
    public static Resolution resolve(Geometry.DisplayFrame display, int sensorId,
                                     List<Observation> input, List<Resolution.Issue> errors) {
        if (sensorId < -1) throw new IllegalArgumentException("Invalid sensor id");
        List<Observation> eligible = new ArrayList<>(); List<Geometry.Bounds> rectangles = new ArrayList<>();
        List<Resolution.Issue> issues = new ArrayList<>(errors); Set<Integer> sensors = new HashSet<>();
        boolean nonUnder = false;
        for (Observation o : input) {
            if (!display.matches(o.displayId)) { issues.add(new Resolution.Issue(o.evidence, WRONG_DISPLAY)); continue; }
            if (sensorId >= 0 && o.sensorId != sensorId) {
                if (o.sensorId == -1) issues.add(new Resolution.Issue(o.evidence, UNBOUND_SENSOR));
                continue;
            }
            if (o.type != Observation.Type.UNKNOWN && !o.type.underDisplay()) { nonUnder = true; continue; }
            if (!o.type.underDisplay() || o.bounds == null) continue;
            try {
                Geometry.Bounds r = display.toLogical(o.bounds, o.space);
                if (o.sensorId >= 0) sensors.add(o.sensorId);
                eligible.add(o); rectangles.add(r);
            } catch (IllegalArgumentException bad) { issues.add(new Resolution.Issue(o.evidence, INVALID_DATA)); }
        }
        if (sensors.size() > 1) return result(AMBIGUOUS, null, null, input, issues, false);
        if (nonUnder && !eligible.isEmpty()) return result(CONFLICT, null, null, input, issues, false);
        if (eligible.isEmpty()) return result(nonUnder ? NOT_UNDER_DISPLAY : denied(issues) ? ACCESS_DENIED : UNAVAILABLE,
                null, null, input, issues, false);
        int chosen = -1;
        for (int i = 0; i < eligible.size(); i++) {
            Observation o = eligible.get(i);
            if (o.frameworkPlaceholder && !confirmed(i, eligible, rectangles)) {
                issues.add(new Resolution.Issue(o.evidence, UNCONFIRMED_DEFAULT)); continue;
            }
            if (chosen < 0 || o.source.priority > eligible.get(chosen).source.priority) chosen = i;
        }
        if (chosen < 0) return result(UNAVAILABLE, null, null, input, issues, false);
        Geometry.Bounds selected = rectangles.get(chosen);
        for (int i = 0; i < eligible.size(); i++) {
            Observation o = eligible.get(i);
            if (o.frameworkPlaceholder || i == chosen) continue;
            if (!compatible(selected, rectangles.get(i))) return result(CONFLICT, null, null, input, issues, false);
        }
        return result(FOUND, selected, eligible.get(chosen), input, issues, confirmed(chosen, eligible, rectangles));
    }
    private static boolean confirmed(int index, List<Observation> candidates, List<Geometry.Bounds> bounds) {
        for (int i = 0; i < candidates.size(); i++)
            if (i != index && !candidates.get(i).frameworkPlaceholder
                    && candidates.get(i).source != candidates.get(index).source && compatible(bounds.get(index), bounds.get(i))) return true;
        return false;
    }
    private static boolean compatible(Geometry.Bounds a, Geometry.Bounds b) {
        double tolerance = Math.max(2, Math.min(Math.min(a.width(), a.height()), Math.min(b.width(), b.height())) / 4);
        return Math.hypot(a.centerX() - b.centerX(), a.centerY() - b.centerY()) <= tolerance;
    }
    private static boolean denied(List<Resolution.Issue> issues) {
        for (Resolution.Issue i : issues) if (i.code == Resolution.Issue.Code.ACCESS_DENIED) return true;
        return false;
    }
    private static Resolution result(Resolution.Status status, Geometry.Bounds bounds, Observation selected,
                                     List<Observation> input, List<Resolution.Issue> issues, boolean corroborated) {
        return new Resolution(status, bounds, selected, input, issues, corroborated);
    }
}
