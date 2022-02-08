/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opentracingshim;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import javax.annotation.Nullable;

final class ScopeManagerShim extends BaseShimObject implements ScopeManager {
  private static final SpanShim NOOP_SPANSHIM =
      new SpanShim(
          new TelemetryInfo(
              OpenTelemetry.noop().getTracer("noop"), OpenTracingPropagators.builder().build()),
          io.opentelemetry.api.trace.Span.getInvalid());

  public ScopeManagerShim(TelemetryInfo telemetryInfo) {
    super(telemetryInfo);
  }

  @Override
  @Nullable
  public Span activeSpan() {
    SpanShim spanShim = SpanShim.current();
    io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
    io.opentelemetry.api.baggage.Baggage baggage = io.opentelemetry.api.baggage.Baggage.current();

    if (!span.getSpanContext().isValid()) {
      if (baggage.isEmpty()) {
        return null;
      }

      return new SpanShim(telemetryInfo(), baggage);
    }

    if (spanShim != null && spanShim.getSpan() == span) {
      return spanShim;
    }

    return new SpanShim(telemetryInfo(), span, baggage);
  }

  @Override
  @SuppressWarnings("MustBeClosedChecker")
  public Scope activate(@Nullable Span span) {
    if (span == null) {
      return new ScopeShim(Context.current().with(NOOP_SPANSHIM).makeCurrent());
    }
    if (!(span instanceof SpanShim)) {
      throw new IllegalArgumentException("span is not a valid SpanShim object");
    }

    SpanShim spanShim = (SpanShim) span;
    return new ScopeShim(Context.current().with(spanShim).makeCurrent());
  }
}
