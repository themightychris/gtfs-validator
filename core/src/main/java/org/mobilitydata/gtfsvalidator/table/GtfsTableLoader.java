/*
 * Copyright 2020 Google LLC
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

package org.mobilitydata.gtfsvalidator.table;

import java.io.InputStream;
import java.util.Set;
import org.mobilitydata.gtfsvalidator.notice.NoticeContainer;
import org.mobilitydata.gtfsvalidator.validator.ValidatorProvider;

/**
 * Loader for a single GTFS table, e.g., stops.txt.
 *
 * <p>Its subclasses are generated by annotation processor based on GTFS schema annotations. The
 * subclasses are annotated with {@code GtfsLoader} to be discovered by {@code GtfsFeedLoader}.
 *
 * @param <T> subclass of {@code GtfsEntity}
 */
public abstract class GtfsTableLoader<T extends GtfsEntity> {
  public abstract String gtfsFilename();

  public abstract boolean isRecommended();

  public abstract boolean isRequired();

  public abstract Set<String> getColumnNames();

  public abstract Set<String> getRequiredColumnNames();

  public abstract GtfsTableContainer<T> load(
      InputStream inputStream,
      ValidatorProvider validatorProvider,
      NoticeContainer noticeContainer);

  public abstract GtfsTableContainer<T> loadMissingFile(
      ValidatorProvider validatorProvider, NoticeContainer noticeContainer);
}
