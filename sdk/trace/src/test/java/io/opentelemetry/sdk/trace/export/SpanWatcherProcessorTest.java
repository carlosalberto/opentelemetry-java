/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link SpanWatchProcessor}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpanWatcherProcessorTest {
  @Mock private ReadWriteSpan readWriteSpan;
  @Mock private SpanProcessor spanProcessor;
  @Mock private Logger logger;
  @Mock private LogRecordBuilder logRecordBuilder;

  private static final String START_SPAN_EVENT_NAME = "otel.sdk.span.start";
  private static final String END_SPAN_EVENT_NAME = "otel.sdk.span.end";
  private static final String HEARTBEAT_SPAN_EVENT_NAME = "otel.sdk.span.heartbeat";

  private static final SpanContext SAMPLED_SPAN_CONTEXT =
      SpanContext.create(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.getSampled(),
          TraceState.getDefault());

  private static final SpanContext NOT_SAMPLED_SPAN_CONTEXT =
      SpanContext.create(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.getDefault(),
          TraceState.getDefault());

  private SpanProcessor spanWatcherProcessor;

  @BeforeEach
  void setUp() {
    spanWatcherProcessor = SpanWatcherProcessor.create(spanProcessor, logger);
    when(spanProcessor.isStartRequired()).thenReturn(true);
    when(spanProcessor.isEndRequired()).thenReturn(true);
    when(readWriteSpan.getKind()).thenReturn(SpanKind.INTERNAL);
    when(readWriteSpan.getInstrumentationScopeInfo())
        .thenReturn(
            InstrumentationScopeInfo.create(SpanWatcherProcessorTest.class.getSimpleName()));
    when(logger.logRecordBuilder()).thenReturn(logRecordBuilder);
    when(logRecordBuilder.setEventName(anyString())).thenReturn(logRecordBuilder);
    when(logRecordBuilder.setAttribute(ArgumentMatchers.<AttributeKey<String>>any(), any()))
        .thenReturn(logRecordBuilder);
    when(logRecordBuilder.setContext(any(Context.class))).thenReturn(logRecordBuilder);
  }

  @AfterEach
  void tearDown() {
    spanWatcherProcessor.shutdown();
  }

  // FIXME: Add shutdown/flush tests.
  @Test
  void onStart_SampledSpan() {
    when(readWriteSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    spanWatcherProcessor.onStart(Context.root(), readWriteSpan);
    verify(spanProcessor).onStart(Context.root(), readWriteSpan);

    verify(logger).logRecordBuilder();
    verify(logRecordBuilder).setEventName(START_SPAN_EVENT_NAME);
    ArgumentCaptor<Context> ctxArgument = ArgumentCaptor.forClass(Context.class);
    verify(logRecordBuilder).setContext(ctxArgument.capture());
    assertThat(SAMPLED_SPAN_CONTEXT)
        .isEqualTo(Span.fromContext(ctxArgument.getValue()).getSpanContext());
    verify(logRecordBuilder).emit();
  }

  @Test
  void onStart_NotSampledSpan() {
    when(readWriteSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanWatcherProcessor.onStart(Context.root(), readWriteSpan);
    verify(spanProcessor).onStart(Context.root(), readWriteSpan);
    verify(logger, never()).logRecordBuilder();
  }

  @Test
  void onEnd_SampledSpan() {
    when(readWriteSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    spanWatcherProcessor.onStart(Context.root(), readWriteSpan);
    spanWatcherProcessor.onEnd(readWriteSpan);
    verify(spanProcessor).onEnd(readWriteSpan);

    verify(logger, times(2)).logRecordBuilder();
    verify(logRecordBuilder).setEventName(START_SPAN_EVENT_NAME);
    verify(logRecordBuilder).setEventName(END_SPAN_EVENT_NAME);
    ArgumentCaptor<Context> ctxArgument = ArgumentCaptor.forClass(Context.class);
    verify(logRecordBuilder, times(2)).setContext(ctxArgument.capture());
    assertThat(SAMPLED_SPAN_CONTEXT)
        .isEqualTo(Span.fromContext(ctxArgument.getValue()).getSpanContext());
    verify(logRecordBuilder, times(2)).emit();
  }

  @Test
  void onEnd_NotSampledSpan() {
    when(readWriteSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanWatcherProcessor.onStart(Context.root(), readWriteSpan);
    spanWatcherProcessor.onEnd(readWriteSpan);
    verify(spanProcessor).onStart(Context.root(), readWriteSpan);
    verify(spanProcessor).onEnd(readWriteSpan);
    verify(logger, never()).logRecordBuilder();
  }

  @Test
  void onEnd_UnwatchedSampledSpan() {
    ReadWriteSpan unwatchedSpan = mock(ReadWriteSpan.class);
    when(unwatchedSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(unwatchedSpan.getKind()).thenReturn(SpanKind.INTERNAL);
    spanWatcherProcessor.onEnd(unwatchedSpan);
    verify(spanProcessor).onEnd(unwatchedSpan); // Must still pass it to the delegate.
    verify(logger, never()).logRecordBuilder();
  }

  @Test
  void heartbeatSpan() {
    int heartbeatIntervalMillis = 15;
    SpanWatcherProcessor processor =
        SpanWatcherProcessor.create(spanProcessor, logger, heartbeatIntervalMillis);
    when(readWriteSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);

    processor.onStart(Context.root(), readWriteSpan);
    try {
      // Wait a little more than 2 cycles, in order to give
      // the watcher time to process the heartbeat twice.
      Thread.sleep(heartbeatIntervalMillis * 3);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
    processor.onEnd(readWriteSpan);

    verify(logger, times(4)).logRecordBuilder();
    verify(logRecordBuilder).setEventName(START_SPAN_EVENT_NAME);
    verify(logRecordBuilder, times(2)).setEventName(HEARTBEAT_SPAN_EVENT_NAME);
    verify(logRecordBuilder).setEventName(END_SPAN_EVENT_NAME);
  }
}
