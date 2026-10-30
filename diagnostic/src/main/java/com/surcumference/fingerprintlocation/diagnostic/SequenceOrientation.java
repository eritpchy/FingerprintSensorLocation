package com.surcumference.fingerprintlocation.diagnostic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SequenceOrientation {
    private final Set<Boolean> unavailable = new HashSet<>();
    private final List<String> failures = new ArrayList<>();

    boolean await(boolean landscape, int width, int height, int attempts) {
        if ((width > height) == landscape) return false;
        if (attempts < 80 && !unavailable.contains(landscape)) return true;
        unavailable.add(landscape);
        return false;
    }

    boolean record(String stage, boolean landscape, int width, int height) {
        boolean satisfied = (width > height) == landscape;
        if (!satisfied) failures.add(stage);
        return satisfied;
    }

    List<String> failures() { return new ArrayList<>(failures); }
}
