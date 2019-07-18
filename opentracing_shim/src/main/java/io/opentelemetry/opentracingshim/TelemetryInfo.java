/*
 * Copyright 2019, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.opentracingshim;

import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Utility class that holds a Tracer, a DistributedContextManager, and related objects that are core
 * part of the OT Shim layer.
 */
final class TelemetryInfo {
  private final Tracer tracer;
  private final DistributedContextManager contextManager;
  private final Map<io.opentelemetry.trace.Span, SpanShim> spanShims;
  private final Map<io.opentelemetry.trace.Span, SpanShim> spanContextShims;
  private final ReadWriteLock spanShimsLock = new ReentrantReadWriteLock();

  TelemetryInfo(Tracer tracer, DistributedContextManager contextManager) {
    this.tracer = tracer;
    this.contextManager = contextManager;
    this.spanShims = new WeakHashMap<>();
  }

  Tracer tracer() {
    return tracer;
  }

  DistributedContextManager contextManager() {
    return contextManager;
  }

  SpanShim getSpanShim(Span span) {
    spanShimsLock.readLock().lock();
    try {
      SpanShim shim = spanShims.get(span);
      if (shim == null) {
        shim = new SpanShim(this, span);
        spanShims.put(span, shim);
      }

      return shim;
    } finally {
      spanShimsLock.readLock().unlock();
    }
  }

  void setSpanShim(Span span, SpanShim shim) {
    spanShimsLock.writeLock().lock();
    try {
      spanShims.put(span, shim);
    } finally {
      spanShimsLock.writeLock().unlock();
    }
  }

  void setBaggageItem(Span span, String key, String value) {
    spanShimsLock.writeLock().lock();
    try {
      spanContextShims.
  }
}
