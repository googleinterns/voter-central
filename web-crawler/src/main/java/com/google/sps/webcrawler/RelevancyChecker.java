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
import com.google.cloud.language.v1.EntityMention;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.cloud.language.v1.LanguageServiceSettings;
import com.google.sps.data.NewsArticle;

// @TODO [Compute the salience of other features, such as location, election, to improve accuracy.]
/** 
 * Provides a tool for checking the relevancy of content to a candidate.
 */
public class RelevancyChecker {
  // @TODO [Calculate a meaningful salience threshold.]
  private static final double salienceThreshold = 0.5;

  /** 
   * Checks whether the {@code newsArticle} is relevant to the {@code candidateName} of
   * interest. Defines relevancy as the salience of {@code candidateName} in the content,
   * and defines sufficient relevancy with {@salienceThreshold}.
   */
  public static boolean isRelevant(NewsArticle newsArticle, String candidateName) {
    double totalSalience = 0;
    int salienceCount = 0;
    for (String content : newsArticle.getContent()) {
      try {
        totalSalience += computeSalienceOfName(content, candidateName);
        salienceCount++;
      } catch (Exception e) {
        continue;
      }
    }
    double averageSalience = salienceCount != 0 ? totalSalience / salienceCount : 0;
    return averageSalience >= salienceThreshold ? true : false;
  }

  /** 
   * Performs entity analysis, and computes the salience score of {@code candidateName} in the
   * {@code content}. Salience has range [0, 1], with higher salience indicating higher
   * relevance of {@code candidateName} to {@code content} overall.
   */
  private static double computeSalienceOfName(String content, String candidateName)
      throws Exception {
    try (LanguageServiceClient language = LanguageServiceClient.create()) {
      Document doc = Document.newBuilder().setContent(content).setType(Type.PLAIN_TEXT).build();
      AnalyzeEntitiesRequest request =
          AnalyzeEntitiesRequest.newBuilder()
              .setDocument(doc)
              .setEncodingType(EncodingType.UTF8)
              .build();
      AnalyzeEntitiesResponse response = language.analyzeEntities(request);
      for (Entity entity : response.getEntitiesList()) {
        if (candidateName.equalsIgnoreCase(entity.getName())) {
          return entity.getSalience();
        }
      }
      return 0;
    }
  }
}
