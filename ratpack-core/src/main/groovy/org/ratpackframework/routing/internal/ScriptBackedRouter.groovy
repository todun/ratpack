/*
 * Copyright 2012 the original author or authors.
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

package org.ratpackframework.routing.internal

import org.ratpackframework.routing.Router
import org.ratpackframework.routing.RouterBuilder
import org.ratpackframework.script.internal.ScriptRunner
import org.ratpackframework.templating.TemplateRenderer
import org.vertx.java.core.Handler
import org.vertx.java.core.Vertx
import org.vertx.java.core.buffer.Buffer
import org.vertx.java.core.file.FileProps
import org.vertx.java.core.file.FileSystem

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ScriptBackedRouter implements Router {

  private final FileSystem fileSystem
  private final String scriptFilePath
  private final TemplateRenderer templateRenderer

  private final AtomicLong lastModifiedHolder = new AtomicLong(-1)
  private final AtomicReference<byte[]> contentHolder = new AtomicReference<byte[]>()
  private final AtomicReference<Router> routerHolder = new AtomicReference<>(null)
  private final Lock lock = new ReentrantLock()

  ScriptBackedRouter(Vertx vertx, File scriptFile, TemplateRenderer templateRenderer) {
    this.fileSystem = vertx.fileSystem()
    this.scriptFilePath = scriptFile.absolutePath
    this.templateRenderer = templateRenderer
  }

  @Override
  void handle(RoutedRequest routedRequest) {
    fileSystem.exists(scriptFilePath, routedRequest.errorHandler.wrap(routedRequest.request, new Handler<Boolean>() {
      @Override
      void handle(Boolean exists) {
        if (!exists) {
          routedRequest.notFoundHandler.handle(routedRequest.request)
          return
        }

        def server = new Router() {
          void handle(RoutedRequest requestToServe) {
            ScriptBackedRouter.this.routerHolder.get().handle(requestToServe)
          }
        }

        def refresher = new Router() {
          void handle(RoutedRequest requestToServe) {
            ScriptBackedRouter.this.lock.lock()
            try {
              refreshSync()
            } finally {
              ScriptBackedRouter.this.lock.unlock()
            }
            server.handle(requestToServe)
          }
        }

        checkForChanges(routedRequest, server, refresher)
      }
    }))
  }

  void checkForChanges(RoutedRequest routedRequest, Router noChange, Router didChange) {
    fileSystem.props(scriptFilePath, routedRequest.errorHandler.wrap(routedRequest.request, new Handler<FileProps>() {
      void handle(FileProps fileProps) {
        if (fileProps.lastModifiedTime.time != lastModifiedHolder.get()) {
          didChange.handle(routedRequest)
          return
        }

        fileSystem.readFile(scriptFilePath, routedRequest.errorHandler.wrap(routedRequest.request, new Handler<Buffer>() {
          @Override
          void handle(Buffer buffer) {
            if (buffer.bytes == ScriptBackedRouter.this.contentHolder.get()) {
              noChange.handle(routedRequest)
            } else {
              didChange.handle(routedRequest)
            }
          }
        }))
      }
    }))

  }

  private void refreshSync() {
    final lastModifiedTime = fileSystem.propsSync(scriptFilePath).lastModifiedTime.time
    final bytes = fileSystem.readFileSync(scriptFilePath).bytes

    if (lastModifiedTime == lastModifiedHolder.get() && bytes == contentHolder.get()) {
      return
    }

    List<Router> routers = []
    def routerBuilder = new RouterBuilder(routers, templateRenderer)
    String string = new String(bytes)
    new ScriptRunner().run(string, routerBuilder)
    routerHolder.set(new CompositeRouter(routers))

    lastModifiedHolder.set(lastModifiedTime)
    contentHolder.set(bytes)
  }

}
