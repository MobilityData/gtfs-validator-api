/*
 * Copyright 2024 MobilityData
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
package org.mobilitydata.gtfsvalidator.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests covering the three endpoints and content negotiation. */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationIntegrationTest {

  @Autowired private MockMvc mockMvc;

  /**
   * Builds a minimal valid GTFS feed as an in-memory ZIP. Generating the fixture at runtime keeps
   * the test self-contained and avoids committing a binary artifact to the repository.
   */
  private MockMultipartFile demoFeed() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put(
        "agency.txt",
        "agency_id,agency_name,agency_url,agency_timezone\n"
            + "1,Demo Transit,https://example.com,America/Toronto\n");
    files.put(
        "calendar.txt",
        "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
            + "WK,1,1,1,1,1,0,0,20240101,20241231\n");
    files.put(
        "routes.txt",
        "route_id,agency_id,route_short_name,route_long_name,route_type\n"
            + "R1,1,1,Demo Route,3\n");
    files.put(
        "stop_times.txt",
        "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
            + "T1,08:00:00,08:00:00,S1,1\n"
            + "T1,08:10:00,08:10:00,S2,2\n");
    files.put(
        "stops.txt",
        "stop_id,stop_name,stop_lat,stop_lon\nS1,Stop 1,45.5,-73.5\nS2,Stop 2,45.6,-73.6\n");
    files.put("trips.txt", "route_id,service_id,trip_id\nR1,WK,T1\n");

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
      for (Map.Entry<String, String> entry : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return new MockMultipartFile("file", "demo-gtfs.zip", "application/zip", buffer.toByteArray());
  }

  @Test
  void metadataReturnsValidatorVersion() throws Exception {
    mockMvc
        .perform(get("/v2/metadata").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.validatorVersion").isNotEmpty());
  }

  @Test
  void uploadReturnsJsonReport() throws Exception {
    mockMvc
        .perform(
            multipart("/v2/validate-upload")
                .file(demoFeed())
                .param("countryCode", "CA")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.summary.validatorVersion").isNotEmpty())
        .andExpect(jsonPath("$.notices").isArray());
  }

  @Test
  void uploadReturnsHtmlReportWhenRequested() throws Exception {
    mockMvc
        .perform(multipart("/v2/validate-upload").file(demoFeed()).accept(MediaType.TEXT_HTML))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("<html")));
  }

  @Test
  void unsupportedAcceptReturns406() throws Exception {
    mockMvc
        .perform(
            multipart("/v2/validate-upload").file(demoFeed()).accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void emptyFileReturns400() throws Exception {
    MockMultipartFile empty =
        new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);
    mockMvc
        .perform(multipart("/v2/validate-upload").file(empty).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("missing_file"));
  }

  @Test
  void invalidUrlSchemeReturns400() throws Exception {
    mockMvc
        .perform(
            post("/v2/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .content("{\"url\":\"ftp://example.org/gtfs.zip\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid_url"));
  }

  @Test
  void wrongContentTypeReturns415() throws Exception {
    mockMvc
        .perform(
            post("/v2/validate-upload")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("not a multipart body"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("unsupported_media_type"));
  }

  @Test
  void missingFilePartReturns400() throws Exception {
    mockMvc
        .perform(multipart("/v2/validate-upload").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad_request"));
  }
}
