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

package ratpack.exec.internal;

import ratpack.exec.ExecResult;
import ratpack.exec.TimedExecResult;

import java.time.Instant;

public final class DefaultTimedExecResult<T> implements TimedExecResult<T> {

  private final ExecResult<T> delegate;
  private final Instant startedAt;
  private final Instant finishedAt;

  public DefaultTimedExecResult(ExecResult<T> delegate, Instant startedAt, Instant finishedAt) {
    this.delegate = delegate;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
  }

  @Override
  public Instant getStartedAt() {
    return startedAt;
  }

  @Override
  public Instant getFinishedAt() {
    return finishedAt;
  }

  @Override
  public boolean isComplete() {
    return delegate.isComplete();
  }

  @Override
  public Throwable getThrowable() {
    return delegate.getThrowable();
  }

  @Override
  public T getValue() {
    return delegate.getValue();
  }

  @Override
  public boolean isSuccess() {
    return delegate.isSuccess();
  }

  @Override
  public boolean isError() {
    return delegate.isError();
  }

  @Override
  public T getValueOrThrow() throws Exception {
    return delegate.getValueOrThrow();
  }
}
