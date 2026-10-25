package io.github.eritpchy.fingerprintlocation.core;

/** Caller-owned, fresh SystemUI service dump. Access and transport remain with the host. */
public interface SystemUiDumpReader {
    String read() throws Exception;
}
