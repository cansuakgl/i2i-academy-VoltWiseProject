package com.wattsmart.backend.common.idempotency;

public interface IdempotencyService {

    boolean tryClaim(String key);
}
