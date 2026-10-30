package com.surcumference.fingerprintlocation.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.surcumference.fingerprintlocation.core.Resolution.Status.*;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.WRONG_DISPLAY;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.UNBOUND_SENSOR;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.INVALID_DATA;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.UNCONFIRMED_DEFAULT;

public final class Resolver {
    private Resolver() {}
    public static Resolution resolve(Geometry.DisplayFrame display, int sensorId,
                                     List<Observation> input, List<Resolution.Issue> errors) {
        if (sensorId < -1) throw new IllegalArgumentException("Invalid sensor id");
        List<Observation> scoped = new ArrayList<>(), eligible = new ArrayList<>();
        List<Geometry.Bounds> rectangles = new ArrayList<>();
        List<Resolution.Issue> issues = new ArrayList<>(errors);
        for (Observation o : input) {
            if (!display.matches(o.displayId)) { issues.add(new Resolution.Issue(o.evidence, WRONG_DISPLAY)); continue; }
            if (sensorId >= 0 && o.sensorId != sensorId) {
                if (o.sensorId == -1) issues.add(new Resolution.Issue(o.evidence, UNBOUND_SENSOR));
                continue;
            }
            scoped.add(o);
            if (!o.type.underDisplay() || o.bounds == null) continue;
            try {
                Geometry.Bounds r = display.toLogical(o.bounds, o.space);
                eligible.add(o); rectangles.add(r);
            } catch (IllegalArgumentException bad) { issues.add(new Resolution.Issue(o.evidence, INVALID_DATA)); }
        }
        TypeDecision type = new TypeDecision(scoped);
        if (type.status == Resolution.TypeStatus.AMBIGUOUS) return result(AMBIGUOUS, null, null, input, issues, false, type);
        if (type.status == Resolution.TypeStatus.CONFLICT) return result(CONFLICT, null, null, input, issues, false, type);
        if (type.type != Observation.Type.UNKNOWN && !type.type.underDisplay())
            return result(NOT_UNDER_DISPLAY, null, null, input, issues, false, type);
        if (eligible.isEmpty()) return result(denied(issues) ? ACCESS_DENIED : UNAVAILABLE,
                null, null, input, issues, false, type);
        int chosen = -1;
        for (int i = 0; i < eligible.size(); i++) {
            Observation o = eligible.get(i);
            if (o.frameworkPlaceholder && !confirmed(i, eligible, rectangles)) {
                issues.add(new Resolution.Issue(o.evidence, UNCONFIRMED_DEFAULT)); continue;
            }
            if (chosen < 0 || o.source.priority > eligible.get(chosen).source.priority) chosen = i;
        }
        if (chosen < 0) return result(UNAVAILABLE, null, null, input, issues, false, type);
        Geometry.Bounds selected = rectangles.get(chosen);
        for (int i = 0; i < eligible.size(); i++) {
            Observation o = eligible.get(i);
            if (o.frameworkPlaceholder || i == chosen) continue;
            if (!compatible(selected, rectangles.get(i))) return result(CONFLICT, null, null, input, issues, false, type);
        }
        return result(FOUND, selected, eligible.get(chosen), input, issues, confirmed(chosen, eligible, rectangles), type);
    }
    private static final class TypeDecision {
        Observation.Type type = Observation.Type.UNKNOWN;
        Resolution.TypeStatus status = Resolution.TypeStatus.UNKNOWN;
        final List<Observation> evidence = new ArrayList<>();
        TypeDecision(List<Observation> observations) {
            Set<Integer> sensors = new HashSet<>();
            boolean conflict = false;
            for (Observation o : observations) {
                if (o.sensorId >= 0) sensors.add(o.sensorId);
                if (o.type == Observation.Type.UNKNOWN) continue;
                evidence.add(o);
                if (type == Observation.Type.UNKNOWN) type = o.type;
                else if (type != o.type) {
                    if (o.type.refines(type)) type = o.type;
                    else if (!type.refines(o.type)) conflict = true;
                }
            }
            if (sensors.size() > 1) status = Resolution.TypeStatus.AMBIGUOUS;
            else if (conflict) status = Resolution.TypeStatus.CONFLICT;
            else if (type != Observation.Type.UNKNOWN) status = Resolution.TypeStatus.KNOWN;
            if (status != Resolution.TypeStatus.KNOWN) type = Observation.Type.UNKNOWN;
        }
    }
    private static boolean configuration(Observation.Source source) {
        return source == Observation.Source.COMMUNITY_ROM_CONFIGURATION
                || source == Observation.Source.STOCK_FIRMWARE_CONFIGURATION
                || source == Observation.Source.CALLER_FIRMWARE_CONFIGURATION;
    }
    private static boolean confirmed(int index, List<Observation> candidates, List<Geometry.Bounds> bounds) {
        if (configuration(candidates.get(index).source)) return false;
        for (int i = 0; i < candidates.size(); i++)
            if (i != index && !candidates.get(i).frameworkPlaceholder
                    && !configuration(candidates.get(i).source)
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
                                     List<Observation> input, List<Resolution.Issue> issues, boolean corroborated, TypeDecision type) {
        return new Resolution(status, bounds, selected, input, issues, corroborated, type.type, type.status, type.evidence);
    }
}
