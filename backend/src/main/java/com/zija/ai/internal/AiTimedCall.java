package com.zija.ai.internal;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Runs an AI task with interruptible cancellation while retaining permits for tasks that ignore it. */
final class AiTimedCall {

    private AiTimedCall() {
    }

    static <T> T execute(
            ExecutorService executor,
            Supplier<T> call,
            AiRequestGuard.Permit permit,
            int timeoutSeconds
    ) throws InterruptedException, ExecutionException, TimeoutException {
        var started = new AtomicBoolean();
        Future<T> task;
        try {
            task = executor.submit(() -> {
                started.set(true);
                try {
                    return call.get();
                } finally {
                    permit.close();
                }
            });
        } catch (RuntimeException exception) {
            permit.close();
            throw exception;
        }

        try {
            return task.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            task.cancel(true);
            if (!started.get()) {
                permit.close();
            }
            throw exception;
        }
    }
}
