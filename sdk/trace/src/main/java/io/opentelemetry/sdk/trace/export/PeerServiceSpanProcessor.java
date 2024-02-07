/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.export;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import javax.annotation.Nullable;

/**
 * Implementation of the {@link SpanProcessor} that uses peer.service stored in TraceState to set it
 * as a Span attribute.
 */
public final class PeerServiceSpanProcessor implements SpanProcessor {

  private static final String OTEL_TRACESTATE_ENTRY_DELIMITER = ";";
  private static final String OTEL_TRACESTATE_KEY_VALUE_DELIMITER = ":";
  private static final String SN_TRACESTATE_KEY = "sn";

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    TraceState ts = Span.fromContext(parentContext).getSpanContext().getTraceState();
    if (!ts.isEmpty() || ts.get(SN_TRACESTATE_KEY) == null) {
      return;
    }

    String peerService = getPeerService(ts.get(SN_TRACESTATE_KEY));
    if (peerService == null || peerService.length() == 0) {
      return;
    }
    span.setAttribute("peer.service", peerService);
  }

  @Nullable
  String getPeerService(String traceStateEntry) {
    String[] entries = traceStateEntry.split(OTEL_TRACESTATE_ENTRY_DELIMITER);
    for (String entry : entries) {
      String[] parts = entry.split(OTEL_TRACESTATE_KEY_VALUE_DELIMITER);
      if (parts.length == 2 && parts[0].equals("ps")) {
        return parts[1];
      }
    }

    return null;
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {}

  @Override
  public boolean isEndRequired() {
    return false;
  }
}
