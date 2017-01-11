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

package ratpack.exec.util

import ratpack.exec.BaseExecutionSpec
import ratpack.exec.Promise

import java.time.Duration

class RecurringFunctionSpec extends BaseExecutionSpec {

  def "can use recurring function"() {
    when:
    def f = RecurringFunction.<Integer> of(
      execHarness.controller,
      { n -> Promise.value(n).defer(Duration.ofSeconds(1)) }, // return the iteration number after 1 sec
      { n, r -> Duration.ofSeconds(1) }
    )

    execHarness.run { f.start().then() }

    then:
    def firstResult = execHarness.yield { f.nextResult }.valueOrThrow
    def almostOneSecond = Duration.ofMillis(900)
    firstResult.duration > almostOneSecond
    firstResult.valueOrThrow == 0

    and:
    def secondResult = execHarness.yield { f.nextResult }.valueOrThrow
    Duration.between(firstResult.finishedAt, secondResult.startedAt) > almostOneSecond
    secondResult.duration > almostOneSecond
    secondResult.valueOrThrow == 1

    cleanup:
    execHarness.run { f.stop().then() }
  }

}
