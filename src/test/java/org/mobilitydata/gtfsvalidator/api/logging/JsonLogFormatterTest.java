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
package org.mobilitydata.gtfsvalidator.api.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Unit tests for the structured JSON log formatter. */
class JsonLogFormatterTest {

  private final JsonLogFormatter formatter = new JsonLogFormatter();
  private final ObjectMapper mapper = new ObjectMapper();
  private final Logger logger = (Logger) LoggerFactory.getLogger("test.logger");

  private JsonNode formatAndParse(LoggingEvent event) throws Exception {
    String line = formatter.format(event);
    assertThat(line).endsWith("\n");
    return mapper.readTree(line);
  }

  private LoggingEvent event(Level level, String message, Throwable throwable) {
    LoggingEvent event =
        new LoggingEvent("fqcn", logger, level, message, throwable, new Object[] {});
    event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
    return event;
  }

  @Test
  void infoEventProducesExpectedFields() throws Exception {
    JsonNode node = formatAndParse(event(Level.INFO, "hello world", null));

    assertThat(node.get("severity").asText()).isEqualTo("INFO");
    assertThat(node.get("message").asText()).isEqualTo("hello world");
    assertThat(node.get("logger").asText()).isEqualTo("test.logger");
    assertThat(node.hasNonNull("timestamp")).isTrue();
    assertThat(node.hasNonNull("thread")).isTrue();
  }

  @Test
  void warnLevelMapsToWarningSeverity() throws Exception {
    JsonNode node = formatAndParse(event(Level.WARN, "careful", null));
    assertThat(node.get("severity").asText()).isEqualTo("WARNING");
  }

  @Test
  void traceLevelMapsToDebugSeverity() throws Exception {
    JsonNode node = formatAndParse(event(Level.TRACE, "trace me", null));
    assertThat(node.get("severity").asText()).isEqualTo("DEBUG");
  }

  @Test
  void throwableIsAppendedToMessage() throws Exception {
    JsonNode node = formatAndParse(event(Level.ERROR, "boom", new IllegalStateException("kaboom")));

    assertThat(node.get("severity").asText()).isEqualTo("ERROR");
    assertThat(node.get("message").asText())
        .startsWith("boom")
        .contains("IllegalStateException")
        .contains("kaboom");
  }

  @Test
  void specialCharactersAreEscaped() throws Exception {
    JsonNode node = formatAndParse(event(Level.INFO, "quote \" and \n newline", null));
    assertThat(node.get("message").asText()).isEqualTo("quote \" and \n newline");
  }
}
