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

package com.google.sps.webcrawler;

import com.google.cloud.language.v1.AnalyzeEntitiesRequest;
import com.google.cloud.language.v1.AnalyzeEntitiesResponse;
import com.google.cloud.language.v1.Document;
import com.google.cloud.language.v1.Document.Type;
import com.google.cloud.language.v1.EncodingType;
import com.google.cloud.language.v1.Entity;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.sps.data.NewsArticle;
import java.io.IOException;

/**
 * A utility class that performs entity analysis to check the relevancy of news article content to a
 * candidate.
 */
public class RelevancyChecker {
  static final double CANDIDATE_SALIENCE_THRESHOLD = 0.18;
  static final double PARTY_SALIENCE_THRESHOLD = 0.1;
  private LanguageServiceClient languageServiceClient;

  /**
   * Constructs a {@code RelevancyChecker} instance to use the Google Natural Language API.
   *
   * @throws IOException if {@code LanguageServiceClient} instantiation fails, such as because of
   *   lack of permission to access the library.
   */
  public RelevancyChecker() throws IOException {
    this(LanguageServiceClient.create());
  }

  /** For testing purposes. */
  RelevancyChecker(LanguageServiceClient languageServiceClient) {
    this.languageServiceClient = languageServiceClient;
  }

  /**
   * Checks whether the {@code newsArticle} is relevant to the {@code candidateName} of interest.
   * Defines relevancy as the salience of {@code candidateName} and {@code partyName} in the
   * content both being bigger than their respective threshold. If {@code partyName} is null,
   * skips the salience checking for {@code partyName} and determines relevancy solely by looking
   * at the salience of {@code candidateName}.
   */
  public boolean isRelevant(NewsArticle newsArticle, String candidateName, String partyName) {
    double candidateNameSalience = computeSalienceOfName(newsArticle.getContent(), candidateName);
    double partyNameSalience =
        (partyName == null)
        ? PARTY_SALIENCE_THRESHOLD : computeSalienceOfName(newsArticle.getContent(), partyName);
    return (candidateNameSalience >= CANDIDATE_SALIENCE_THRESHOLD
        && partyNameSalience >= PARTY_SALIENCE_THRESHOLD);
  }

  /**
   * Performs entity analysis, and computes the salience score of {@code name} in the {@code
   * content}. Salience has range [0, 1], with higher salience indicating higher relevance of
   * {@code name} to {@code content} overall.
   */
  double computeSalienceOfName(String content, String name) {
    Document doc = Document.newBuilder().setContent(content).setType(Type.PLAIN_TEXT).build();
    AnalyzeEntitiesRequest request =
        AnalyzeEntitiesRequest.newBuilder()
            .setDocument(doc)
            .setEncodingType(EncodingType.UTF8)
            .build();
    AnalyzeEntitiesResponse response = languageServiceClient.analyzeEntities(request);
    for (Entity entity : response.getEntitiesList()) {
      if (name.equalsIgnoreCase(entity.getName())) {
        return entity.getSalience();
      }
    }
    return 0;
  }
}
