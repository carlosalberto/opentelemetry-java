/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace.samplers;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.Immutable;

/**
 * Adaptive sampler that given a trace id ratio, also uses the upcoming service data to try to
 * balance the count of traces kept for each service, based on a specific time cycle.
 *
 * <p>This is done by keeping track of: 1) Entire population count. 2) Each service's population
 * count. 3) Each services's sampled count.
 *
 * <p>With the information above, it is possible to know whether, in the current cycle, a trace has
 * been sufficiently represented or not, and can thus be kept or dropped.
 */
@Immutable
final class AdaptiveSampler implements Sampler {
  private static final int CYCLE_DURATION_MS = 1000;
  private static final String OT_ENTRY_KEY = "ot";
  private static final String PEER_SERVICE_KEY = "us";

  Map<String, PeerServiceEntry> peerServiceEntries = new HashMap<String, PeerServiceEntry>();
  long nextDeadline;
  long totalPopulationCount;
  long totalSampledCount;
  final TraceIdRatioBasedSampler probabilitySampler;
  final Object lock = new Object();

  public Sampler create(double ratio) {
    return new AdaptiveSampler(ratio);
  }

  AdaptiveSampler(double ratio) {
    this.probabilitySampler = TraceIdRatioBasedSampler.create(ratio);
  }

  static final class PeerServiceEntry {
    int populationCount;
    int sampledCount;
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {

    String peerService = getPeerFromContext(parentContext);
    SamplingResult samplingResult =
        probabilitySampler.shouldSample(
            parentContext, traceId, name, spanKind, attributes, parentLinks);

    long currentTimeMillis = System.currentTimeMillis();
    synchronized (lock) {
      if (currentTimeMillis >= nextDeadline) {
        nextDeadline = currentTimeMillis + CYCLE_DURATION_MS;
        totalPopulationCount = 0;
        totalSampledCount = 0;
        for (PeerServiceEntry entry : peerServiceEntries.values()) {
          entry.populationCount = 0;
          entry.sampledCount = 0;
        }
      }

      PeerServiceEntry entry = peerServiceEntries.get(peerService);
      if (entry == null) {
        entry = new PeerServiceEntry();
        peerServiceEntries.put(peerService, entry);
      }
      totalPopulationCount++;
      entry.populationCount++;

      boolean sampled = samplingResult.getDecision() == SamplingDecision.RECORD_AND_SAMPLE;

      if (sampled) {
        if (totalSampledCount == 0 // provide a sampling 'seed' and prevent totalSampledCount == 0
            || (entry.populationCount / totalPopulationCount)
                >= (entry.sampledCount / totalSampledCount)) {
          entry.sampledCount++;
          totalSampledCount++;
          return samplingResult;
        }
      }

      // FIXME: Properly handle SamplingDecision.RECORD.
      return SamplingResult.drop();
    }
  }

  @Override
  public String getDescription() {
    return probabilitySampler.getDescription();
  }

  static String getPeerFromContext(Context parentContext) {
    TraceState ts = Span.fromContext(parentContext).getSpanContext().getTraceState();
    String otEntry = ts.get(OT_ENTRY_KEY);

    if (otEntry != null && otEntry.length() > 0) {
      for (String otSubEntry : otEntry.split(";")) {
        String[] parts = otSubEntry.split(":");
        if (parts.length == 2 && PEER_SERVICE_KEY.equals(parts[0])) {
          return parts[1];
        }
      }
    }

    // Like semconv, use this term to group requests without peer service.
    return "unknown_service";
  }
}
