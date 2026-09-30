package com.skyzzz.guardian.api.violation;

import java.util.List;
import java.util.UUID;

/** Historical violation log. Memory-backed by default; SQLite and MySQL are opt-in. */
public interface ViolationStore {

    void init() throws Exception;

    void close();

    void record(ViolationRecord record);

    List<ViolationRecord> history(UUID uuid, int limit);

    List<ViolationRecord> recent(int limit);

    void flush();
}