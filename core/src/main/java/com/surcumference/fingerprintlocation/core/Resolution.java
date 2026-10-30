package com.surcumference.fingerprintlocation.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Resolution {
    public enum Status { FOUND, UNAVAILABLE, ACCESS_DENIED, NOT_UNDER_DISPLAY, AMBIGUOUS, CONFLICT }
    public enum TypeStatus { KNOWN, UNKNOWN, AMBIGUOUS, CONFLICT }
    public static final class Issue {
        public enum Code { UNAVAILABLE, ACCESS_DENIED, INVALID_DATA, WRONG_DISPLAY, DISPLAY_CHANGED, UNBOUND_SENSOR, UNCONFIRMED_DEFAULT, WRONG_THREAD, TIMEOUT, UNTRUSTED_SOURCE }
        public final String provider;
        public final Code code;
        public final String exceptionType;
        public Issue(String provider, Code code) { this(provider, code, null); }
        public Issue(String provider, Code code, Throwable error) {
            this.provider = provider; this.code = code;
            this.exceptionType = error == null ? "" : error.getClass().getName();
        }
        @Override public String toString() { return provider + ":" + code; }
    }
    public final Status status;
    public final Geometry.Bounds bounds;
    public final Observation selected;
    public final List<Observation> observations;
    public final List<Issue> issues;
    public final boolean corroborated;
    public final Observation.Type sensorType;
    public final TypeStatus typeStatus;
    public final List<Observation> typeEvidence;

    Resolution(Status status, Geometry.Bounds bounds, Observation selected,
               List<Observation> observations, List<Issue> issues, boolean corroborated) {
        this(status, bounds, selected, observations, issues, corroborated,
                Observation.Type.UNKNOWN, TypeStatus.UNKNOWN, Collections.emptyList());
    }
    Resolution(Status status, Geometry.Bounds bounds, Observation selected,
               List<Observation> observations, List<Issue> issues, boolean corroborated,
               Observation.Type sensorType, TypeStatus typeStatus, List<Observation> typeEvidence) {
        this.status = status; this.bounds = bounds; this.selected = selected;
        this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues)); this.corroborated = corroborated;
        this.sensorType = sensorType; this.typeStatus = typeStatus;
        this.typeEvidence = Collections.unmodifiableList(new ArrayList<>(typeEvidence));
    }
}
