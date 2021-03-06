package io.github.pepperkit.retry;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.github.pepperkit.retry.Retry.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryTest {

    @Test
    @Timeout(6)
    void fixedRetry() {
        AtomicInteger counter = new AtomicInteger();

        retry(3)
                .backoff(new BackoffFunction.Fixed())
                .delay(Duration.ofSeconds(2))
                .handle(IllegalArgumentException.class)
                .onFailure(e -> {
                    if (e instanceof IllegalArgumentException) {
                        System.out.println("I caught you!");
                    }
                })
                .run(() -> {
                    counter.incrementAndGet();
                    throw new IllegalArgumentException("Just to test");
                });

        assertEquals(3, counter.get());
    }

    @Test
    void exponentialWhenResultIsSuccess() {
        Optional<String> result = retry(1)
                .backoff(new BackoffFunction.Exponential(3))
                .delay(Duration.ofSeconds(2))
                .call(() -> "RESULT");

        assertEquals(Optional.of("RESULT"), result);
    }

    @Test
    @Timeout(6)
    void multipleHandleExceptions() {
        AtomicInteger counter = new AtomicInteger();

        retry(3)
                .backoff(new BackoffFunction.Fixed())
                .delay(Duration.ofSeconds(2))
                .handle(Set.of(IllegalArgumentException.class, IllegalStateException.class,
                        IllegalCallerException.class))
                .run(() -> {
                    counter.incrementAndGet();
                    if (counter.get() == 1) {
                        throw new IllegalArgumentException("Just to test");
                    }

                    if (counter.get() == 2) {
                        throw new IllegalStateException("Just to test");
                    }

                    if (counter.get() == 3) {
                        throw new IllegalCallerException("Just to test");
                    }
                });

        assertEquals(3, counter.get());
    }

    @Test
    void shouldAbortIfThrowsException() {
        retry()
                .abortIf(IllegalStateException.class)
                .run(() -> {
                    throw new IllegalStateException("Abort if");
                });
    }

    @Test
    void shouldAbortIfWithMultipleExceptions() {
        AtomicInteger counter = new AtomicInteger(0);
        retry(5)
                .backoff(new BackoffFunction.Fixed())
                .abortIf(Set.of(IllegalStateException.class, IllegalCallerException.class))
                .run(() -> {
                    counter.incrementAndGet();

                    if (counter.get() == 3) {
                        throw new IllegalCallerException("Abort if");
                    }

                    throw new IllegalArgumentException("Just to retry");
                });

        assertEquals(3, counter.get());
    }

    @Timeout(9)
    @Test
    void shouldBeLimitedByMaxRetryInterval() {
        retry(3)
                .backoff(new BackoffFunction.Exponential(5))
                .delay(Duration.ofSeconds(2))
                .maxDelay(Duration.ofSeconds(3))
                .run(() -> {
                    throw new IllegalStateException("");
                });
    }

    @Test
    void shouldUseBackoffFunction() {
        retry(2)
                .delay(Duration.ofSeconds(2))
                .maxDelay(Duration.ofSeconds(3))
                .run(() -> {
                    throw new IllegalStateException("");
                });
    }

    @Test
    void shouldSelectMinDelay() {
        retry(2)
                .delay(Duration.ofSeconds(2))
                .maxDelay(Duration.ofSeconds(30))
                .run(() -> {
                    throw new IllegalStateException("");
                });
    }

    @Test
    void onFailureWithCall() {
        assertThrows(IllegalStateException.class, () ->
                retry(3)
                        .backoff(new BackoffFunction.Exponential(3))
                        .delay(Duration.ofSeconds(2))
                        .handle(IllegalStateException.class)
                        .onFailure(ex -> {
                            if (ex instanceof IllegalArgumentException) {
                                throw new IllegalStateException("Got it!");
                            }
                        })
                        .call(() -> {
                            throw new IllegalArgumentException("On Failure");
                        }));
    }
}
