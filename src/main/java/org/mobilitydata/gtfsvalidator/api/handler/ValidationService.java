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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.mobilitydata.gtfsvalidator.input.CountryCode;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunnerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs the GTFS validator core against a feed and exposes the produced JSON and HTML reports.
 *
 * <p>Each invocation runs synchronously in an isolated temporary output directory which is deleted
 * before the call returns.
 */
@Service
public class ValidationService {

  private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

  private static final String JSON_REPORT_NAME = "report.json";
  private static final String HTML_REPORT_NAME = "report.html";
  private static final String SYSTEM_ERRORS_REPORT_NAME = "system_errors.json";

  private final ValidationRunner runner;

  public ValidationService(ValidationRunner runner) {
    this.runner = runner;
  }

  /** Holder for the rendered validation reports. */
  public record Reports(String json, String html) {}

  /**
   * Validates a feed located at a remote URL.
   *
   * @param url public URL of a GTFS ZIP
   * @param countryCode optional ISO 3166-1 alpha-2 country code (may be {@code null}/blank)
   */
  public Reports validateFromUrl(URI url, String countryCode) {
    if (url == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "A feed url is required.");
    }
    if (url.getScheme() == null
        || !(url.getScheme().equalsIgnoreCase("http")
            || url.getScheme().equalsIgnoreCase("https"))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "invalid_url", "Feed URL must be an http(s) URL: " + url);
    }
    return validate(url, countryCode);
  }

  /**
   * Validates a feed from an uploaded ZIP stream.
   *
   * @param zipStream stream of the uploaded GTFS ZIP
   * @param countryCode optional ISO 3166-1 alpha-2 country code (may be {@code null}/blank)
   */
  public Reports validateFromUpload(InputStream zipStream, String countryCode) {
    Path uploadFile = null;
    try {
      uploadFile = Files.createTempFile("gtfs-upload-", ".zip");
      Files.copy(zipStream, uploadFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return validate(uploadFile.toUri(), countryCode);
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "unprocessable_feed",
          "Could not read the uploaded feed.",
          e);
    } finally {
      deleteQuietly(uploadFile);
    }
  }

  private Reports validate(URI source, String countryCode) {
    Path outputDirectory = null;
    try {
      outputDirectory = Files.createTempDirectory("gtfs-validation-");

      ValidationRunnerConfig.Builder configBuilder =
          ValidationRunnerConfig.builder()
              .setGtfsSource(source)
              .setOutputDirectory(outputDirectory)
              .setValidationReportFileName(JSON_REPORT_NAME)
              .setHtmlReportFileName(HTML_REPORT_NAME)
              .setSystemErrorsReportFileName(SYSTEM_ERRORS_REPORT_NAME)
              .setStdoutOutput(false)
              // Skip the remote update check; rely on the bundled validator version.
              .setSkipValidatorUpdate(true);

      if (countryCode != null && !countryCode.isBlank()) {
        configBuilder.setCountryCode(CountryCode.forStringOrUnknown(countryCode));
      }

      ValidationRunner.Status status = runner.run(configBuilder.build());
      if (status != ValidationRunner.Status.SUCCESS) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "unprocessable_feed",
            "The feed could not be validated. It may be corrupt or not a valid GTFS archive.");
      }

      Path jsonReport = outputDirectory.resolve(JSON_REPORT_NAME);
      Path htmlReport = outputDirectory.resolve(HTML_REPORT_NAME);
      if (!Files.exists(jsonReport)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "validator_crashed",
            "The validator did not produce a report for the feed.");
      }

      String json = Files.readString(jsonReport);
      String html = Files.exists(htmlReport) ? Files.readString(htmlReport) : null;
      return new Reports(json, html);
    } catch (ApiException e) {
      throw e;
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "unprocessable_feed",
          "An error occurred while validating the feed.",
          e);
    } finally {
      deleteRecursivelyQuietly(outputDirectory);
    }
  }

  private void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      logger.warn("Could not delete temp file {}", path, e);
    }
  }

  private void deleteRecursivelyQuietly(Path directory) {
    if (directory == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
    } catch (IOException e) {
      logger.warn("Could not delete temp directory {}", directory, e);
    }
  }
}
