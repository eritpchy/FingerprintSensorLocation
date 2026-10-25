package com.surcumference.fingerprintlocation.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Probe {
    private final List<Observation> observations = new ArrayList<>();
    private final List<Resolution.Issue> issues = new ArrayList<>();
    public void add(Observation observation) { observations.add(java.util.Objects.requireNonNull(observation)); }
    public void issue(String provider, Resolution.Issue.Code code) { issues.add(new Resolution.Issue(provider, code)); }
    public List<Observation> observations() { return Collections.unmodifiableList(observations); }
    public List<Resolution.Issue> issues() { return Collections.unmodifiableList(issues); }
    public Resolution resolve(Geometry.DisplayFrame display, int sensorId) {
        return Resolver.resolve(display, sensorId, observations, issues);
    }
    public Resolution unavailable() {
        boolean denied = false;
        for (Resolution.Issue issue : issues) denied |= issue.code == Resolution.Issue.Code.ACCESS_DENIED;
        return new Resolution(denied ? Resolution.Status.ACCESS_DENIED : Resolution.Status.UNAVAILABLE,
                null, null, observations, issues, false);
    }
}
