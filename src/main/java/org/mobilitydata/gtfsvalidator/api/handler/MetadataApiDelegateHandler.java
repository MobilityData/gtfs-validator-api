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

import org.mobilitydata.gtfsvalidator.api.gen.MetadataApiDelegate;
import org.mobilitydata.gtfsvalidator.api.model.ServiceMetadata;
import org.mobilitydata.gtfsvalidator.api.model.ServiceMetadataLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** Implements the {@code /metadata} endpoint. */
@Service
public class MetadataApiDelegateHandler implements MetadataApiDelegate {

  private final VersionProvider versionProvider;
  private final Integer maxUploadBytes;
  private final Integer maxRequestsPerMinute;

  public MetadataApiDelegateHandler(
      VersionProvider versionProvider,
      @Value("${gtfs.validator.limits.max-upload-bytes:#{null}}") Integer maxUploadBytes,
      @Value("${gtfs.validator.limits.max-requests-per-minute:#{null}}")
          Integer maxRequestsPerMinute) {
    this.versionProvider = versionProvider;
    this.maxUploadBytes = maxUploadBytes;
    this.maxRequestsPerMinute = maxRequestsPerMinute;
  }

  @Override
  public ResponseEntity<ServiceMetadata> getMetadata() {
    ServiceMetadata metadata = new ServiceMetadata(versionProvider.getValidatorVersion());
    if (maxUploadBytes != null || maxRequestsPerMinute != null) {
      ServiceMetadataLimits limits = new ServiceMetadataLimits();
      limits.setMaxUploadBytes(maxUploadBytes);
      limits.setMaxRequestsPerMinute(maxRequestsPerMinute);
      metadata.setLimits(limits);
    }
    return ResponseEntity.ok(metadata);
  }
}
