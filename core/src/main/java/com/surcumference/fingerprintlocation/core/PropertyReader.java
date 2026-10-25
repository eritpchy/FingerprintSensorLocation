package com.surcumference.fingerprintlocation.core;

/** Returns an empty string or null when a property is unavailable. Does not request privileges. */
public interface PropertyReader {
    String get(String name) throws Exception;
}
