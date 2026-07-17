package com.unifiedcalendar.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncConfigTest {

    @Test
    void emailExecutorUsesConfiguredBoundsAndRejectsOverflow() throws Exception {
        ThreadPoolTaskExecutor executor = new AsyncConfig().emailTaskExecutor(1, 1, 1);
        executor.initialize();
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                firstTaskStarted.countDown();
                try {
                    releaseFirstTask.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(firstTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();

            executor.execute(() -> { });

            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(TaskRejectedException.class);
        } finally {
            releaseFirstTask.countDown();
            executor.shutdown();
        }
    }
}
