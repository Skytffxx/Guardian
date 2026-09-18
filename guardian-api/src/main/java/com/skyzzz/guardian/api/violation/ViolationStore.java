package com.skyzzz.guardian.api.violation;

import java.util.List;
import java.util.UUID;

/**
 * Historical violation log. Storage is optional: {@code MemoryViolationStore} is
 * always available and SQLite/MySQL are opt-in. Guardian runs fine without a DB.
 */
public interface ViolationStore {

    void init() throws Exception;

    void close();

    void record(ViolationRecord record);

    List<ViolationRecord> history(UUID uuid, int limit);

    List<ViolationRecord> recent(int limit);

    void flush();
}