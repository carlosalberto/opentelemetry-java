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

package io.opentelemetry.contextprop;

import static io.opentelemetry.trace.propagation.ContextKeys.getSpanContextKey;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.baggage.DefaultBaggageManager;
import io.opentelemetry.baggage.propagation.DefaultBaggageExtractor;
import io.opentelemetry.baggage.propagation.DefaultBaggageInjector;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ChainedPropagators;
import io.opentelemetry.context.propagation.HttpExtractor;
import io.opentelemetry.context.propagation.HttpInjector;
import io.opentelemetry.distributedcontext.CorrelationContext;
import io.opentelemetry.distributedcontext.LabelKey;
import io.opentelemetry.distributedcontext.LabelMetadata;
import io.opentelemetry.distributedcontext.LabelValue;
import io.opentelemetry.distributedcontext.propagation.DefaultCorrelationContextExtractor;
import io.opentelemetry.distributedcontext.propagation.DefaultCorrelationContextInjector;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.Tracestate;
import io.opentelemetry.trace.propagation.HttpTraceContextExtractor;
import io.opentelemetry.trace.propagation.HttpTraceContextInjector;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PropagatorsTest {
  @Test
  public void testSimpleRequest() {
    Map<String, String> inboundCarrier = new HashMap<>();

    // Chain the extractors.
    HttpExtractor extractor =
        ChainedPropagators.chain(
            ChainedPropagators.chain(
                new HttpTraceContextExtractor(), new DefaultCorrelationContextExtractor()),
            new DefaultBaggageExtractor());

    // Chain the injectors.
    HttpInjector injector =
        ChainedPropagators.chain(
            ChainedPropagators.chain(
                new HttpTraceContextInjector(), new DefaultCorrelationContextInjector()),
            new DefaultBaggageInjector());

    // Initialize by injecting an empty SpanContext.
    Context initialCtx =
        Context.current()
            .setValue(
                getSpanContextKey(),
                SpanContext.create(
                    TraceId.getInvalid(),
                    SpanId.getInvalid(),
                    TraceFlags.getDefault(),
                    Tracestate.getDefault()));
    injector.inject(initialCtx, inboundCarrier, new MapSetter());

    // Extract.
    Context ctx = extractor.extract(Context.current(), inboundCarrier, new MapGetter());

    Scope scope = Context.setCurrent(ctx);
    try {
      // Explicit style (pass Context, use opaque object underneath).
      Context newCtx =
          DefaultBaggageManager.getInstance().setValue(Context.current(), "mykey", "myvalue");
      Scope bagScope = Context.setCurrent(newCtx);
      try {

        // Implicit style (do not pass Context, use specific interface/object).
        CorrelationContext corrCtx =
            OpenTelemetry.getCorrelationContextManager()
                .contextBuilder()
                .setParent(OpenTelemetry.getCorrelationContextManager().getCurrentContext())
                .put(
                    LabelKey.create("key1"),
                    LabelValue.create("label1"),
                    LabelMetadata.create(LabelMetadata.HopLimit.UNLIMITED_PROPAGATION))
                .build();
        Scope corrScope = OpenTelemetry.getCorrelationContextManager().withContext(corrCtx);
        try {
          // Inject everything that is active at this point.
          Map<String, String> outboundCarrier = new HashMap<>();
          injector.inject(Context.current(), outboundCarrier, new MapSetter());
        } finally {
          corrScope.close();
        }
      } finally {
        bagScope.close();
      }
    } finally {
      scope.close();
    }
  }

  static final class MapGetter implements HttpExtractor.Getter<Map<String, String>> {
    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  }

  static final class MapSetter implements HttpInjector.Setter<Map<String, String>> {
    @Override
    public void put(Map<String, String> carrier, String key, String value) {
      carrier.put(key, value);
    }
  }
}
