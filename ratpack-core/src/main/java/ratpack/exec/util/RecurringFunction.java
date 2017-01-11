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

package ratpack.exec.util;

import ratpack.exec.ExecController;
import ratpack.exec.Operation;
import ratpack.exec.Promise;
import ratpack.exec.TimedExecResult;
import ratpack.exec.util.internal.DefaultRecurringFunction;
import ratpack.func.BiFunction;
import ratpack.func.Block;
import ratpack.func.Function;

import java.time.Clock;
import java.time.Duration;

// Note: will be part of Ratpack 1.5

public interface RecurringFunction<T> {

  enum State {
    PENDING,
    EXECUTING,
    STOPPED
  }

  State getState();

  Operation start();

  Operation stop();

  Promise<TimedExecResult<T>> getNextResult();

  TimedExecResult<T> getPreviousResult();

  Integer getInvocations();

  default RecurringFunction<T> onStart(Block onStart) {
    return onStart(Operation.of(onStart));
  }

  RecurringFunction<T> onStart(Operation onStart);

  default RecurringFunction<T> onStop(Block onStop) {
    return onStop(Operation.of(onStop));
  }

  RecurringFunction<T> onStop(Operation onStop);

  static <T> RecurringFunction<T> of(
    Function<? super Integer, ? extends Promise<T>> function,
    BiFunction<? super Integer, ? super TimedExecResult<T>, Duration> resultListener
  ) {
    return of(ExecController.require(), Clock.systemUTC(), function, resultListener);
  }

  static <T> RecurringFunction<T> of(
    ExecController execController,
    Function<? super Integer, ? extends Promise<T>> function,
    BiFunction<? super Integer, ? super TimedExecResult<T>, Duration> resultListener
  ) {
    return of(execController, Clock.systemUTC(), function, resultListener);
  }

  static <T> RecurringFunction<T> of(
    ExecController execController,
    Clock clock,
    Function<? super Integer, ? extends Promise<T>> function,
    BiFunction<? super Integer, ? super TimedExecResult<T>, Duration> resultListener
  ) {
    return new DefaultRecurringFunction<>(execController, clock, function, resultListener);
  }

}
