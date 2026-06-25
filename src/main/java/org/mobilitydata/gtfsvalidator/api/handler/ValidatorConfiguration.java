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

import org.mobilitydata.gtfsvalidator.runner.ApplicationType;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the GTFS validator core components ({@link VersionResolver} and {@link ValidationRunner})
 * as Spring beans so they can be injected into the API delegate handlers.
 */
@Configuration
public class ValidatorConfiguration {

  @Bean
  public VersionResolver versionResolver() {
    return new VersionResolver(ApplicationType.WEB);
  }

  @Bean
  public ValidationRunner validationRunner(VersionResolver versionResolver) {
    //   Warning: ValidationRunner is not thread safe.
    //    Calling the service in a concurrent scenario may lead to unexpected
    //    memory usage report, validation rules and the rest of the report is not compromised.
    //    see: https://github.com/MobilityData/gtfs-validator/issues/2168
    return new ValidationRunner(versionResolver);
  }
}
