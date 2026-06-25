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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * Emits one structured JSON object per log line, intended for cloud log aggregators that parse
 * stdout. Each entry carries a {@code severity}, {@code message} and ISO-8601 {@code timestamp},
 * plus the logger name, thread, MDC values and any structured key/value arguments. Exception stack
 * traces are appended to {@code message} so error trackers can pick them up.
 *
 * <p>Activated only under the {@code json} Spring profile (see {@code
 * application-json.properties}); the default profile keeps the human-readable console output.
 */
public class JsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

  private final ThrowableProxyConverter throwableConverter = new ThrowableProxyConverter();

  public JsonLogFormatter() {
    throwableConverter.start();
  }

  @Override
  public String format(ILoggingEvent event) {
    StringBuilder json = new StringBuilder(256);
    json.append('{');
    writeField(json, "timestamp", TIMESTAMP.format(event.getInstant()), true);
    writeField(json, "severity", severity(event.getLevel()), false);
    writeField(json, "logger", event.getLoggerName(), false);
    writeField(json, "thread", event.getThreadName(), false);
    writeField(json, "message", message(event), false);

    for (Map.Entry<String, String> mdc : event.getMDCPropertyMap().entrySet()) {
      writeField(json, mdc.getKey(), mdc.getValue(), false);
    }
    if (event.getKeyValuePairs() != null) {
      for (KeyValuePair pair : event.getKeyValuePairs()) {
        writeField(json, pair.key, String.valueOf(pair.value), false);
      }
    }
    json.append('}').append('\n');
    return json.toString();
  }

  /** Maps logback levels onto the severities understood by common cloud log collectors. */
  private static String severity(Level level) {
    if (level == null) {
      return "DEFAULT";
    }
    if (level == Level.WARN) {
      return "WARNING";
    }
    if (level == Level.TRACE) {
      return "DEBUG";
    }
    return level.toString();
  }

  private String message(ILoggingEvent event) {
    String message = event.getFormattedMessage();
    if (event.getThrowableProxy() == null) {
      return message;
    }
    String stackTrace = throwableConverter.convert(event);
    return (message == null || message.isEmpty()) ? stackTrace : message + "\n" + stackTrace;
  }

  private static void writeField(StringBuilder json, String key, String value, boolean first) {
    if (value == null) {
      return;
    }
    if (!first) {
      json.append(',');
    }
    appendEscaped(json, key);
    json.append(':');
    appendEscaped(json, value);
  }

  private static void appendEscaped(StringBuilder json, String value) {
    json.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        case '\b' -> json.append("\\b");
        case '\f' -> json.append("\\f");
        default -> {
          if (c < 0x20) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    json.append('"');
  }
}
