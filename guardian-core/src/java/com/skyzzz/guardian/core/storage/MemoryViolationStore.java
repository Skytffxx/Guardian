package com.skyzzz.guardian.core.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.skyzzz.guardian.api.violation.ViolationRecord;
import com.skyzzz.guardian.api.violation.ViolationStore;

public final class MemoryViolationStore implements ViolationStore {

    private final Deque<ViolationRecord> records = new ArrayDeque<>();
    private final int capacity;

    public MemoryViolationStore() {
        this(5000);
    }

    public MemoryViolationStore(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void init() {
        // nothing to do
    }

    @Override
    public synchronized void close() {
        records.clear();
    }

    @Override
    public synchronized void record(ViolationRecord record) {
        records.addFirst(record);
        while (records.size() > capacity) {
            records.removeLast();
        }
    }

    @Override
    public synchronized List<ViolationRecord> history(UUID uuid, int limit) {
        List<ViolationRecord> result = new ArrayList<>();
        for (ViolationRecord record : records) {
            if (record.uuid().equals(uuid)) {
                result.add(record);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public synchronized List<ViolationRecord> recent(int limit) {
        List<ViolationRecord> result = new ArrayList<>();
        for (ViolationRecord record : records) {
            result.add(record);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public void flush() {
        // nothing to do
    }
}