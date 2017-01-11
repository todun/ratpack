/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.exec.util.internal;

import ratpack.exec.*;
import ratpack.exec.util.Promised;
import ratpack.exec.util.RecurringFunction;
import ratpack.func.BiFunction;
import ratpack.func.Function;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultRecurringFunction<T> implements RecurringFunction<T> {

  private final ExecController execController;

  private final Clock clock;
  private final Function<? super Integer, ? extends Promise<T>> function;
  private final BiFunction<? super Integer, ? super TimedExecResult<T>, Duration> resultListener;

  private Operation onStart = Operation.noop();
  private Operation onStop = Operation.noop();

  private volatile ScheduledFuture<?> scheduledFuture;

  private volatile Promised<TimedExecResult<T>> nextResult = new Promised<>();
  private volatile TimedExecResult<T> previousResult;

  private volatile State state = State.STOPPED;

  private final AtomicInteger counter = new AtomicInteger();
  private final Throttle throttle = Throttle.ofSize(1);

  public DefaultRecurringFunction(ExecController execController, Clock clock, Function<? super Integer, ? extends Promise<T>> function, BiFunction<? super Integer, ? super TimedExecResult<T>, Duration> resultListener) {
    this.execController = execController;
    this.clock = clock;
    this.function = function;
    this.resultListener = resultListener;

    nextResult.complete();
  }

  @Override
  public State getState() {
    return state;
  }

  @Override
  public RecurringFunction<T> onStart(Operation onStart) {
    this.onStart = onStart;
    return this;
  }

  @Override
  public RecurringFunction<T> onStop(Operation onStop) {
    this.onStop = onStop;
    return this;
  }

  @Override
  public Operation start() {
    return throttle.throttle(Promise.sync(() -> {
      if (state == State.STOPPED) {
        nextResult = new Promised<>();
        state = State.EXECUTING;
        onStart
          .onError(e -> {
            state = State.STOPPED;
            nextResult.error(e);
          })
          .then(this::execute);
      }
      return null;
    })).operation();
  }

  @Override
  public Operation stop() {
    return Operation.of(() -> {
      State previousState = this.state;
      this.state = State.STOPPED;
      if (previousState == State.PENDING) {
        scheduledFuture.cancel(false);
        onStop
          .onError(nextResult::error)
          .then(nextResult::complete);
      }
    }).promise().throttled(throttle).operation();
  }

  @Override
  public TimedExecResult<T> getPreviousResult() {
    return previousResult;
  }

  @Override
  public Integer getInvocations() {
    return counter.get();
  }

  @Override
  public Promise<TimedExecResult<T>> getNextResult() {
    return nextResult.promise();
  }

  private void execute() {
    Execution.fork()
      .start(Operation.of(() -> {
        if (state == State.STOPPED) { // has been stopped since we were scheduled
          return;
        }

        scheduledFuture = null;

        state = State.EXECUTING;
        Instant startedAt = clock.instant();

        int num = counter.getAndIncrement();
        Promise.flatten(() -> function.apply(num))
          .result(r -> {
            TimedExecResult<T> timedResult = TimedExecResult.of(r, startedAt, clock.instant());
            previousResult = timedResult;
            Duration delay;
            Promised<TimedExecResult<T>> promised = nextResult;
            nextResult = new Promised<>();
            try {
              delay = resultListener.apply(num, timedResult);
            } catch (Throwable t) {
              state = State.STOPPED;
              nextResult.complete();
              promised.error(t);
              return;
            }

            promised.success(timedResult);
            if (delay == null) {
              state = State.STOPPED;
              nextResult.complete();
            } else if (delay.isZero() || delay.isNegative()) {
              state = State.PENDING;
              execute();
            } else {
              state = State.PENDING;
              scheduledFuture = execController.getExecutor().schedule(this::execute, delay.toNanos(), TimeUnit.NANOSECONDS);
            }
          });

      }).promise().throttled(throttle).operation());

  }
}
