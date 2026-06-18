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

import org.mobilitydata.gtfsvalidator.api.model.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to the API {@code Error} response schema. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Error> handleApiException(ApiException ex) {
    if (ex.getStatus().is5xxServerError()) {
      logger.error("API error", ex);
    } else {
      logger.debug("API error: {}", ex.getMessage());
    }
    return ResponseEntity.status(ex.getStatus()).body(error(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Error> handlePayloadTooLarge(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(error("payload_too_large", "The uploaded file exceeds the configured size limit."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Error> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldError() != null
            ? ex.getBindingResult().getFieldError().getField()
                + ": "
                + ex.getBindingResult().getFieldError().getDefaultMessage()
            : "Request validation failed.";
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("bad_request", message));
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<Error> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
        .body(
            error(
                "not_acceptable", "Supported response types are application/json and text/html."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Error> handleNotFound(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            error(
                "not_found",
                "No such endpoint: " + ex.getResourcePath() + ". The API base path is /v2."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Error> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(error("method_not_allowed", ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> handleUnexpected(Exception ex) {
    logger.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(error("internal_error", "An unexpected error occurred."));
  }

  private Error error(String code, String message) {
    return new Error(code, message);
  }
}
