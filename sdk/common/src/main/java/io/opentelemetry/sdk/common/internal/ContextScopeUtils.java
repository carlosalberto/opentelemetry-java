/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.common.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContextScopeUtils {
  static final ContextKey<Attributes> KEY = ContextKey.named("opentelemetry-context-scoped-attrs");

  private ContextScopeUtils() {}

  public static Context setContextScopedAttributes(Context context, Attributes attributes) {
    return context.with(KEY, attributes);
  }

  public static Attributes getContextScopedAttributes(Context context) {
    Attributes ctxAttributes = context.get(KEY);
    return ctxAttributes == null ? Attributes.empty() : ctxAttributes;
  }
}
