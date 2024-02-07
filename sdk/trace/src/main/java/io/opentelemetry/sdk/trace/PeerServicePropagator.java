/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public final class PeerServicePropagator implements TextMapPropagator {

  private static final String TRACE_STATE = "tracestate";
  private static final int TRACESTATE_MAX_SIZE = 512;
  private static final List<String> FIELDS =
      Collections.unmodifiableList(Arrays.asList(TRACE_STATE));

  private static final char TRACESTATE_KEY_VALUE_DELIMITER = '=';
  private static final char TRACESTATE_ENTRY_DELIMITER = ',';
  private static final String SN_TRACESTATE_KEY = "sn";
  private static final AttributeKey<String> PEER_SERVICE_KEY =
      AttributeKey.stringKey("peer.service");

  @Nullable String serviceName;

  public PeerServicePropagator(Resource resource) {
    this.serviceName = resource.getAttribute(PEER_SERVICE_KEY);
  }

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
    if (context == null || setter == null) {
      return;
    }

    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    if (!spanContext.isValid()) {
      return;
    }

    TraceState traceState = spanContext.getTraceState();
    if (traceState.isEmpty()) {
      // No need to add an empty "tracestate" header.
      return;
    }

    setter.set(carrier, TRACE_STATE, encodeTraceState(traceState));
  }

  @Override
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    // The main TraceContext propagator will automatically extract this.
    return context;
  }

  /** Return the trace state encoded as a string according to the W3C specification. */
  public String encodeTraceState(TraceState traceState) {
    TraceStateBuilder builder = new TraceStateBuilder();
    traceState.forEach(
        (key, value) -> {
          builder.addEntry(key, value);
        });

    // Add or update the existing entry.
    builder.addEntry(SN_TRACESTATE_KEY, "ps:" + serviceName);

    return builder.toString();
  }

  static class TraceStateBuilder {
    StringBuilder builder = new StringBuilder(TRACESTATE_MAX_SIZE);

    public void addEntry(String key, String value) {
      if (builder.length() != 0) {
        builder.append(TRACESTATE_ENTRY_DELIMITER);
      }

      builder.append(key).append(TRACESTATE_KEY_VALUE_DELIMITER).append(value);
    }

    @Override
    public String toString() {
      return builder.toString();
    }
  }
}
