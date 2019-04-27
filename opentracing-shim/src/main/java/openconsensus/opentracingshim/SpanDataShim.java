/*
 * Copyright 2019, OpenConsensus Authors
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

package openconsensus.opentracingshim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import openconsensus.resource.Resource;
import openconsensus.trace.AttributeValue;
import openconsensus.trace.Link;
import openconsensus.trace.Span.Kind;
import openconsensus.trace.SpanContext;
import openconsensus.trace.SpanData;
import openconsensus.trace.SpanData.TimedEvent;
import openconsensus.trace.SpanData.Timestamp;
import openconsensus.trace.SpanId;
import openconsensus.trace.Status;

final class SpanDataShim {
  private final SpanContext context;
  private final SpanId parentSpanId;
  private final Resource resource;
  private final Kind kind;
  private final Timestamp startTimestamp;
  private final Map<String, AttributeValue> attributes;
  private final List<TimedEvent> timedEvents;
  private final List<Link> links;

  private String name;
  private Status status;
  private Timestamp endTimestamp;

  public SpanDataShim(
      SpanContext context,
      SpanId parentSpanId,
      Resource resource,
      String name,
      Kind kind,
      Timestamp startTimestamp,
      Map<String, AttributeValue> attributes,
      List<Link> links) {

    this.context = context;
    this.parentSpanId = parentSpanId;
    this.resource = resource;
    this.name = name;
    this.kind = kind;
    this.startTimestamp = startTimestamp;
    this.attributes = attributes;
    this.links = links;

    this.timedEvents = new ArrayList<>();
    this.status = Status.OK;
  }

  public SpanContext getContext() {
    return context;
  }

  public SpanId getParentSpanId() {
    return parentSpanId;
  }

  public Resource getResource() {
    return resource;
  }

  public String getName() {
    return name;
  }

  void updateName(String name) {
    this.name = name;
  }

  public Kind getKind() {
    return kind;
  }

  public Timestamp getStartTimestamp() {
    return startTimestamp;
  }

  public Map<String, AttributeValue> getAttributes() {
    return attributes;
  }

  public List<TimedEvent> getTimedEvents() {
    return timedEvents;
  }

  public List<Link> getLinks() {
    return links;
  }

  public Status getStatus() {
    return status;
  }

  void setStatus(Status status) {
    this.status = status;
  }

  public Timestamp getEndTimestamp() {
    return endTimestamp;
  }

  void setEndTimestamp(Timestamp endTimestamp) {
    this.endTimestamp = endTimestamp;
  }

  public SpanData createSpanData() {
    return SpanData.create(
        context,
        parentSpanId,
        resource,
        name,
        kind,
        startTimestamp,
        attributes,
        timedEvents,
        links,
        status,
        endTimestamp);
  }
}
