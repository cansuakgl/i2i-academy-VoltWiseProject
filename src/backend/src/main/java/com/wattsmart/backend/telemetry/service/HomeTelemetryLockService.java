package com.wattsmart.backend.telemetry.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class HomeTelemetryLockService {

    private final ConcurrentHashMap<UUID, ReentrantLock> locksByHomeId = new ConcurrentHashMap<>();

    public void withHomeLock(UUID homeId, Runnable work) {
        ReentrantLock lock = locksByHomeId.computeIfAbsent(homeId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            work.run();
        } finally {
            lock.unlock();
        }
    }
}
