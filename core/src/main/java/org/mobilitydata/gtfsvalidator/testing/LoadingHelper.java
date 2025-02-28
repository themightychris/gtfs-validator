/*
 * Copyright 2022 Google LLC
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
package org.mobilitydata.gtfsvalidator.testing;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.mobilitydata.gtfsvalidator.input.CountryCode;
import org.mobilitydata.gtfsvalidator.input.CurrentDateTime;
import org.mobilitydata.gtfsvalidator.notice.NoticeContainer;
import org.mobilitydata.gtfsvalidator.notice.ValidationNotice;
import org.mobilitydata.gtfsvalidator.table.GtfsEntity;
import org.mobilitydata.gtfsvalidator.table.GtfsTableContainer;
import org.mobilitydata.gtfsvalidator.table.GtfsTableLoader;
import org.mobilitydata.gtfsvalidator.validator.DefaultValidatorProvider;
import org.mobilitydata.gtfsvalidator.validator.ValidationContext;
import org.mobilitydata.gtfsvalidator.validator.ValidatorLoader;
import org.mobilitydata.gtfsvalidator.validator.ValidatorLoaderException;
import org.mobilitydata.gtfsvalidator.validator.ValidatorProvider;

/** Convenience methods for loading files in unit-tests. */
public class LoadingHelper {

  private CountryCode countryCode = CountryCode.forStringOrUnknown("ca");
  private ZonedDateTime currentTime = ZonedDateTime.of(2021, 1, 1, 14, 30, 0, 0, ZoneOffset.UTC);

  private NoticeContainer noticeContainer = new NoticeContainer();

  public List<ValidationNotice> getValidationNotices() {
    return noticeContainer.getValidationNotices();
  }

  public <X extends GtfsEntity, Y extends GtfsTableContainer<X>> Y load(
      GtfsTableLoader<X> loader, String... lines) throws ValidatorLoaderException {
    String content = Arrays.stream(lines).collect(Collectors.joining("\n"));
    InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    ValidationContext context =
        ValidationContext.builder()
            .setCountryCode(countryCode)
            .setCurrentDateTime(new CurrentDateTime(currentTime))
            .build();
    // We explicitly do not scan and load all validators, because we want to keep the set of
    // validators used in unit-tests minimal and stable.  If unit-tests do need more validators
    // loaded, consider adding methods for specifying specific validators, as opposed to loading
    // all.
    ValidatorLoader validatorLoader = new ValidatorLoader(ImmutableList.of());
    ValidatorProvider provider = new DefaultValidatorProvider(context, validatorLoader);
    return (Y) loader.load(in, provider, noticeContainer);
  }
}
