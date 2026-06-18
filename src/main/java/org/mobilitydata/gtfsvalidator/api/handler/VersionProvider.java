/*
 * Copyright 2026 MobilityData
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mobilitydata.gtfsvalidator.api.handler;

import java.time.Duration;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;
import org.springframework.stereotype.Component;

/**
 * Provides validator version information for the {@code /metadata} endpoint.
 *
 * <p>The version reported here is the version of the bundled GTFS validator core, resolved from the
 * validator's {@link VersionResolver}. It is intentionally independent of this API project's own
 * version.
 */
@Component
public class VersionProvider {

  private static final Duration RESOLVE_TIMEOUT = Duration.ofSeconds(5);

  private final VersionResolver versionResolver;

  public VersionProvider(VersionResolver versionResolver) {
    this.versionResolver = versionResolver;
  }

  /**
   * Returns the validator core version, or {@code "unknown"} if it cannot be resolved (e.g. when
   * running from exploded classes without a JAR manifest).
   */
  public String getValidatorVersion() {
    return versionResolver
        .getVersionInfoWithTimeout(RESOLVE_TIMEOUT, true)
        .currentVersion()
        .orElse("unknown");
  }
}
