/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.internal.DaemonThreadFactory;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SpanWatcherProcessor implements SpanProcessor {
  private final SpanProcessor processor;
  private final Thread workerThread;
  private final Worker worker;

  private static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 60 * 1000;

  private static final String WORKER_THREAD_NAME =
      SpanWatcherProcessor.class.getSimpleName() + "_WorkerThread";

  // TODO: Add builder with other options including:
  // - Max watch set.
  // - Watch for unsampled spans too?
  public static SpanWatcherProcessor create(
      SpanProcessor processor, Logger logger, long heartbeatIntervalMillis) {
    return new SpanWatcherProcessor(processor, logger, heartbeatIntervalMillis);
  }

  public static SpanWatcherProcessor create(SpanProcessor processor, Logger logger) {
    return new SpanWatcherProcessor(processor, logger, DEFAULT_HEARTBEAT_INTERVAL_MILLIS);
  }

  private SpanWatcherProcessor(
      SpanProcessor processor, Logger logger, long heartbeatIntervalMillis) {
    this.processor = processor;
    this.worker = new Worker(logger, heartbeatIntervalMillis, 512);
    this.workerThread = new DaemonThreadFactory(WORKER_THREAD_NAME).newThread(worker);
    this.workerThread.start();
  }

  @Override
  public void onStart(Context context, ReadWriteSpan span) {
    if (processor.isStartRequired()) {
      processor.onStart(context, span);
    }
    if (span.getSpanContext().getTraceFlags().isSampled()) {
      worker.spanStarted(span);
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    if (processor.isEndRequired()) {
      processor.onEnd(span);
    }
    if (span != null && span.getSpanContext().getTraceFlags().isSampled()) {
      worker.spanEnded(span);
    }
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  @Override
  public CompletableResultCode shutdown() {
    worker.markShutdown();
    return processor.shutdown();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return processor.forceFlush();
  }

  @Override
  public void close() {
    worker.markShutdown();
    processor.close();
  }

  private static final class Worker implements Runnable {
    private final Logger logger;
    private final long heartbeatIntervalMillis;
    private final int maxWatchSetSize;
    private final Set<ReadableSpan> watchSet;
    private final Object monitor = new Object();
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    private Worker(Logger logger, long heartbeatIntervalMillis, int maxWatchSetSize) {
      this.logger = logger;
      this.heartbeatIntervalMillis = heartbeatIntervalMillis;
      this.maxWatchSetSize = maxWatchSetSize;
      this.watchSet = Collections.newSetFromMap(new WeakHashMap<ReadableSpan, Boolean>());
    }

    private void spanStarted(ReadableSpan span) {
      synchronized (monitor) {
        if (watchSet.size() >= maxWatchSetSize) {
          // TODO: Report either a metric or a simple error message.
          return;
        }

        watchSet.add(span);
      }

      // TODO: Add attributes + parent SpanContext.
      // TODO: pre-allocate keys when the prototype becomes part of the incubating artifact.
      // FIXME: We should be recording the span/trace/parent/flags/state directly here.
      // FIXME: Here but not in heartbeats: SpanKind, spancontext info, iscope.
      InstrumentationScopeInfo scopeInfo = span.getInstrumentationScopeInfo();
      logger
          .logRecordBuilder()
          .setEventName("otel.sdk.span.start")
          .setAttribute(AttributeKey.stringKey("otel.span.name"), span.getName())
          .setAttribute(AttributeKey.stringKey("otel.span.kind"), span.getKind().toString())
          .setAttribute(
              AttributeKey.stringKey("otel.instrumentationscope.name"), scopeInfo.getName())
          .setAttribute(
              AttributeKey.stringKey("otel.instrumentationscope.version"), scopeInfo.getVersion())
          .setAttribute(
              AttributeKey.stringKey("otel.instrumentationscope.schemaurl"),
              scopeInfo.getSchemaUrl())
          .setAttribute(
              AttributeKey.stringKey("otel.span.trace_id"), span.getSpanContext().getTraceId())
          .setAttribute(
              AttributeKey.stringKey("otel.span.span_id"), span.getSpanContext().getSpanId())
          .setAttribute(
              AttributeKey.stringKey("otel.span.trace_flags"),
              span.getSpanContext().getTraceFlags().asHex())
          .setContext(Context.root().with(Span.wrap(span.getSpanContext())))
          .emit();
    }

    private void spanEnded(ReadableSpan span) {
      boolean wasSpanRemoved;
      synchronized (monitor) {
        wasSpanRemoved = watchSet.remove(span);
      }

      // Do not report events we were not watching.
      if (!wasSpanRemoved) {
        return;
      }

      logger
          .logRecordBuilder()
          .setEventName("otel.sdk.span.end")
          .setContext(Context.root().with(Span.wrap(span.getSpanContext())))
          .emit();
    }

    private void reportHeartbeatEvents(ReadableSpan[] spans) {
      for (ReadableSpan span : spans) {
        logger
            .logRecordBuilder()
            .setEventName("otel.sdk.span.heartbeat")
            .setContext(Context.root().with(Span.wrap(span.getSpanContext())))
            .emit();
      }
    }

    private void markShutdown() {
      isShutdown.set(true);
    }

    @Override
    public void run() {
      while (!isShutdown.get()) {
        ReadableSpan[] spans;
        synchronized (monitor) {
          try {
            monitor.wait(heartbeatIntervalMillis);
          } catch (InterruptedException e) {
            // TODO: Handle so-called spurious wake ups.
            isShutdown.set(true);
            Thread.currentThread().interrupt();
            return;
          }

          spans = watchSet.toArray(new ReadableSpan[0]);
        }

        reportHeartbeatEvents(spans);
      }
    }
  }
}
