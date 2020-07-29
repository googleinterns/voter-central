// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.infocompiler;

/**
 * A configuration class for keeping hidden API keys and other configuration files.
 */
public class Config {
  public static final String PROJECT_ID = "";
  public static final String ADDRESS_BUCKET_NAME = "";
  public static final String ADDRESS_FILE_NAME = "";
  public static final String CIVIC_INFO_API_KEY = "";
  public static final String CUSTOM_SEARCH_KEY = "";
  public static final String CUSTOM_SEARCH_ENGINE_ID = "";
  public static final String OPEN_NLP_MODEL_FILES_BUCKET_NAME = "";
  public static final String OPEN_NLP_SENTENCE_DETECTOR_FILE = "en-sent.bin";
  public static final String OPEN_NLP_TOKENIZER_FILE = "en-token.bin";

  // For respecting the query rate limit (250 queries/100 seconds) of the Civic Information API:
  // With Cloud Functions deployment: How much to shorten/extend the pause between queries, relative
  // to the minimum pause (0.4 seconds) required.
  // Recommended value: 2.
  public static final double PAUSE_FACTOR = 2;
  // Due to Cloud Functions' 540s execution limit: process only a subset of addresses.
  // For instance: [0, 300), [301, 600), [601, 1000) respectively for three Cloud Functions.
  public static final int ADDRESS_START_INDEX = 0; // Lower-bounded by 0.
  public static final int ADDRESS_END_INDEX = 300; // Upper-bounded by the total number of addresses.
}
