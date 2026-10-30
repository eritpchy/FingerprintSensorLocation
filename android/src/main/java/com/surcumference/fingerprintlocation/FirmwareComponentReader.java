package com.surcumference.fingerprintlocation;

import java.io.InputStream;

/** Supplies current-device bytes using access already authorized by the caller.
 * The library closes the stream, verifies its hash and never obtains elevated access.
 * Implementations must bound opening and reading time and must not substitute archive bytes.
 */
public interface FirmwareComponentReader {
    InputStream open(String path) throws Exception;
}
