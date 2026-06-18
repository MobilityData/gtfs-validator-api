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

import java.io.IOException;
import org.mobilitydata.gtfsvalidator.api.gen.ValidationApiDelegate;
import org.mobilitydata.gtfsvalidator.api.model.ValidateByUrlRequest;
import org.mobilitydata.gtfsvalidator.api.model.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implements the validation endpoints. Runs the GTFS validator core and returns the resulting
 * report either as JSON ({@code application/json}, default) or as the rendered HTML document
 * ({@code text/html}) based on the request's {@code Accept} header.
 */
@Service
public class ValidationApiDelegateHandler implements ValidationApiDelegate {

  private static final Logger logger = LoggerFactory.getLogger(ValidationApiDelegateHandler.class);

  private final ValidationService validationService;

  public ValidationApiDelegateHandler(ValidationService validationService) {
    this.validationService = validationService;
  }

  @Override
  public ResponseEntity<ValidationReport> validateByUrl(ValidateByUrlRequest validateByUrlRequest) {
    if (validateByUrlRequest == null || validateByUrlRequest.getUrl() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "A feed url is required.");
    }
    logger.debug("Validating feed by url: {}", validateByUrlRequest.getUrl());
    ValidationService.Reports reports =
        validationService.validateFromUrl(
            validateByUrlRequest.getUrl(), validateByUrlRequest.getCountryCode());
    return toResponse(reports);
  }

  @Override
  public ResponseEntity<ValidationReport> validateByUpload(MultipartFile file, String countryCode) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "missing_file", "A non-empty feed file is required.");
    }
    logger.debug(
        "Validating uploaded feed: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
    try {
      ValidationService.Reports reports =
          validationService.validateFromUpload(file.getInputStream(), countryCode);
      return toResponse(reports);
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "unprocessable_feed",
          "Could not read the uploaded feed.",
          e);
    }
  }

  /**
   * Builds the response in the negotiated format. The generic type is erased at runtime, so we
   * return either the raw JSON report string or the rendered HTML document with the matching
   * content type.
   */
  @SuppressWarnings("unchecked")
  private ResponseEntity<ValidationReport> toResponse(ValidationService.Reports reports) {
    if (wantsHtml()) {
      if (reports.html() == null) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "validator_crashed",
            "The validator did not produce an HTML report for the feed.");
      }
      return (ResponseEntity<ValidationReport>)
          (ResponseEntity<?>)
              ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(reports.html());
    }
    return (ResponseEntity<ValidationReport>)
        (ResponseEntity<?>)
            ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(reports.json());
  }

  /** Returns true when the client prefers {@code text/html} over {@code application/json}. */
  private boolean wantsHtml() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return false;
    }
    String accept = servletAttributes.getRequest().getHeader("Accept");
    if (accept == null || accept.isBlank()) {
      return false;
    }
    for (MediaType mediaType : MediaType.parseMediaTypes(accept)) {
      if (mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
        return false;
      }
      if (mediaType.isCompatibleWith(MediaType.TEXT_HTML)) {
        return true;
      }
    }
    return false;
  }
}
