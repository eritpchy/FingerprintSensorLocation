package io.github.eritpchy.fingerprintlocation.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Resolution {
    public enum Status { FOUND, UNAVAILABLE, ACCESS_DENIED, NOT_UNDER_DISPLAY, AMBIGUOUS, CONFLICT }
    public static final class Issue {
        public enum Code { UNAVAILABLE, ACCESS_DENIED, INVALID_DATA, WRONG_DISPLAY, DISPLAY_CHANGED, UNBOUND_SENSOR, UNCONFIRMED_DEFAULT }
        public final String provider;
        public final Code code;
        public Issue(String provider, Code code) { this.provider = provider; this.code = code; }
        @Override public String toString() { return provider + ":" + code; }
    }
    public final Status status;
    public final Geometry.Bounds bounds;
    public final Observation selected;
    public final List<Observation> observations;
    public final List<Issue> issues;
    public final boolean corroborated;

    Resolution(Status status, Geometry.Bounds bounds, Observation selected,
               List<Observation> observations, List<Issue> issues, boolean corroborated) {
        this.status = status; this.bounds = bounds; this.selected = selected;
        this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues)); this.corroborated = corroborated;
    }
}
