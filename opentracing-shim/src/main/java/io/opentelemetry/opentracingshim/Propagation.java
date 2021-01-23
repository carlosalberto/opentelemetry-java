/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opentracingshim;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

final class Propagation extends BaseShimObject {
  TextMapGetter textMapGetter;
  TextMapSetter textMapSetter;

  Propagation(TelemetryInfo telemetryInfo, boolean urlEncoding) {
    super(telemetryInfo);
    this.textMapGetter = new TextMapGetter(urlEncoding);
    this.textMapSetter = new TextMapSetter(urlEncoding);
  }

  public void injectTextMap(SpanContextShim contextShim, TextMapInject carrier) {
    Context context = Context.current().with(Span.wrap(contextShim.getSpanContext()));
    context = context.with(contextShim.getBaggage());

    propagators().getTextMapPropagator().inject(context, carrier, textMapSetter);
  }

  @Nullable
  public SpanContextShim extractTextMap(TextMapExtract carrier) {
    Map<String, String> carrierMap = new HashMap<>();
    for (Map.Entry<String, String> entry : carrier) {
      carrierMap.put(entry.getKey(), entry.getValue());
    }

    Context context =
        propagators().getTextMapPropagator().extract(Context.current(), carrierMap, textMapGetter);

    Span span = Span.fromContext(context);
    if (!span.getSpanContext().isValid()) {
      return null;
    }

    return new SpanContextShim(telemetryInfo, span.getSpanContext(), Baggage.fromContext(context));
  }

  static final class TextMapSetter implements TextMapPropagator.Setter<TextMapInject> {
    final boolean urlEncoding;

    TextMapSetter(boolean urlEncoding) {
      this.urlEncoding = urlEncoding;
    }

    @Override
    public void set(TextMapInject carrier, String key, String value) {
      carrier.put(key, encodedValue(value));
    }

    private String encodedValue(String value) {
      if (!urlEncoding) {
        return value;
      }
      try {
        return URLEncoder.encode(value, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        // not much we can do, try raw value
        return value;
      }
    }
  }

  // We use Map<> instead of TextMap as we need to query a specified key, and iterating over
  // *all* values per key-query *might* be a bad idea.
  static final class TextMapGetter implements TextMapPropagator.Getter<Map<String, String>> {
    final boolean urlEncoding;

    TextMapGetter(boolean urlEncoding) {
      this.urlEncoding = urlEncoding;
    }

    @Nullable
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      for (Map.Entry<String, String> entry : carrier.entrySet()) {
        if (key.equalsIgnoreCase(entry.getKey())) {
          return decodedValue(entry.getValue());
        }
      }
      return null;
    }

    private String decodedValue(String value) {
      if (!urlEncoding) {
        return value;
      }
      try {
        return URLDecoder.decode(value, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        // not much we can do, try raw value
        return value;
      }
    }
  }
}
