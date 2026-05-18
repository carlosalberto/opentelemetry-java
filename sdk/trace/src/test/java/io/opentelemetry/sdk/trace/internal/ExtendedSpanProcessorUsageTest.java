/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Demonstrating usage of {@link ExtendedSpanProcessor}. */
class ExtendedSpanProcessorUsageTest {

  private static final AttributeKey<String> FOO_KEY = AttributeKey.stringKey("foo");
  private static final AttributeKey<String> BAR_KEY = AttributeKey.stringKey("bar");

  private static final AttributeKey<String> NAME_KEY = AttributeKey.stringKey("otel.span.name");
  private static final AttributeKey<String> ATTRIBUTES_KEY =
      AttributeKey.stringKey("otel.attributes");
  private static final AttributeKey<String> ATTRIBUTE_K_KEY =
      AttributeKey.stringKey("otel.attribute.key");
  private static final AttributeKey<String> ATTRIBUTE_V_KEY =
      AttributeKey.stringKey("otel.attribute.value");
  private static final AttributeKey<String> TRACE_ID_KEY =
      AttributeKey.stringKey("otel.span.trace_id");
  private static final AttributeKey<String> SPAN_ID_KEY =
      AttributeKey.stringKey("otel.span.span_id");

  private static class CopyFooToBarProcessor implements ExtendedSpanProcessor {

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {}

    @Override
    public boolean isStartRequired() {
      return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {}

    @Override
    public boolean isEndRequired() {
      return false;
    }

    @Override
    public void onEnding(ReadWriteSpan span) {
      String val = span.getAttribute(FOO_KEY);
      span.setAttribute(BAR_KEY, val);
    }

    @Override
    public boolean isOnEndingRequired() {
      return true;
    }
  }

  @Test
  void extendedSpanProcessorUsage() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();

    try (SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .addSpanProcessor(new CopyFooToBarProcessor())
            .build()) {

      Tracer tracer = tracerProvider.get("dummy-tracer");
      Span span = tracer.spanBuilder("my-span").startSpan();

      span.setAttribute(FOO_KEY, "Hello!");

      span.end();

      assertThat(exporter.getFinishedSpanItems())
          .hasSize(1)
          .first()
          .satisfies(
              spanData -> {
                assertThat(spanData.getAttributes())
                    .containsEntry(FOO_KEY, "Hello!")
                    .containsEntry(BAR_KEY, "Hello!");
              });
    }
  }

  @Test
  void spanChangedUsage() {
    InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();

    try (SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build()) {
      try (SdkTracerProvider tracerProvider =
          SdkTracerProvider.builder()
              .addSpanProcessor(new SpanChangedProcessor(loggerProvider))
              .build()) {

        Tracer tracer = tracerProvider.get("dummy-tracer");
        Span span = tracer.spanBuilder("my-span").startSpan();

        // 1. Update Name
        span.updateName("foo-bar-span");

        // 2. Set Attribute
        span.setAttribute(FOO_KEY, "Hello!");

        // 3. Add Link.
        IdGenerator idGenerator = IdGenerator.random();
        String traceId = idGenerator.generateTraceId();
        String spanId = idGenerator.generateSpanId();
        span.addLink(
            SpanContext.create(traceId, spanId, TraceFlags.getDefault(), TraceState.getDefault()));

        span.end();

        List<LogRecordData> finishedLogRecords = exporter.getFinishedLogRecordItems();
        assertThat(finishedLogRecords).hasSize(3);
        assertThat(finishedLogRecords.get(0))
            .satisfies(
                logRecordData -> {
                  assertThat(logRecordData.getEventName()).isEqualTo("otel.span.name_updated");
                  assertThat(logRecordData.getAttributes()).containsEntry(NAME_KEY, "foo-bar-span");
                });
        assertThat(finishedLogRecords.get(1))
            .satisfies(
                logRecordData -> {
                  assertThat(logRecordData.getEventName()).isEqualTo("otel.span.attribute_set");
                  assertThat(logRecordData.getAttributes())
                      .containsEntry(ATTRIBUTE_K_KEY, FOO_KEY.toString());
                  assertThat(logRecordData.getAttributes())
                      .containsEntry(ATTRIBUTE_V_KEY, "Hello!");
                });
        assertThat(finishedLogRecords.get(2))
            .satisfies(
                logRecordData -> {
                  assertThat(logRecordData.getEventName()).isEqualTo("otel.span.link_added");
                  assertThat(logRecordData.getAttributes()).containsEntry(TRACE_ID_KEY, traceId);
                  assertThat(logRecordData.getAttributes()).containsEntry(SPAN_ID_KEY, spanId);
                });
      }
    }
  }

  private static class SpanChangedProcessor implements ExtendedSpanProcessor {

    private final SdkLoggerProvider loggerProvider;

    SpanChangedProcessor(SdkLoggerProvider loggerProvider) {
      this.loggerProvider = loggerProvider;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {}

    @Override
    public boolean isStartRequired() {
      return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {}

    @Override
    public boolean isEndRequired() {
      return false;
    }

    @Override
    public void onEnding(ReadWriteSpan span) {}

    @Override
    public boolean isOnEndingRequired() {
      return false;
    }

    @Override
    public void onUpdateName(ReadableSpan span) {
      loggerProvider
          .get("test")
          .logRecordBuilder()
          .setEventName("otel.span.name_updated")
          .setAttribute(NAME_KEY, span.getName())
          .emit();
    }

    @Override
    public void onSetAttribute(ReadableSpan span, AttributeKey<?> key, Object value) {
      loggerProvider
          .get("test")
          .logRecordBuilder()
          .setEventName("otel.span.attribute_set")
          .setAttribute(ATTRIBUTE_K_KEY, key.toString())
          .setAttribute(ATTRIBUTE_V_KEY, value.toString())
          .emit();
    }

    @Override
    public void onAddLink(ReadableSpan span, SpanContext spanContext, Attributes attributes) {
      loggerProvider
          .get("test")
          .logRecordBuilder()
          .setEventName("otel.span.link_added")
          .setAttribute(TRACE_ID_KEY, spanContext.getTraceId())
          .setAttribute(SPAN_ID_KEY, spanContext.getSpanId())
          .setAttribute(ATTRIBUTES_KEY, attributes.toString())
          .emit();
    }
  }
}
