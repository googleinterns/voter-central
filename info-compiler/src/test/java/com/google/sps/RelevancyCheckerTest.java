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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.Mockito.*;

import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.sps.data.NewsArticle;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xml.sax.SAXException;

/**
 * A tester for the relevancy checker of news articles.
 */
@RunWith(JUnit4.class)
public final class RelevancyCheckerTest {
  private static final String URL = "https://www.cnn.com/index.html";
  private static final String RELEVANT_CONTENT = "Alexandria Ocasio-Cortez is A.O.C.";
  private static final String IRRELEVANT_CONTENT = "Irrelevant content.";
  private static final String CANDIDATE_NAME = "Alexandria Ocasio-Cortez";
  private static final int PRIORITY = 1;

  private LanguageServiceClient languageServiceClient;
  private RelevancyChecker mockRelevancyChecker;
  private RelevancyChecker relevancyChecker;

  @Before
  public void initialize() throws IOException {
    mockRelevancyChecker = mock(RelevancyChecker.class);
    LanguageServiceClient languageServiceClient = mock(LanguageServiceClient.class);
    relevancyChecker = new RelevancyChecker(languageServiceClient);
  }

  @Test
  public void computeSalienceOfName_findsEverythingRelevant() {
    // Check content relevancy with a mock relevancy checker that always computes a salience
    // score higher than {@code SALIENCE_THRESHOLD}.
    NewsArticle relevantNewsArticle = new NewsArticle(URL, null, null, PRIORITY);
    relevantNewsArticle.setContent(RELEVANT_CONTENT);
    NewsArticle irrelevantNewsArticle = new NewsArticle(URL, null, null, PRIORITY);
    irrelevantNewsArticle.setContent(IRRELEVANT_CONTENT);
    when(mockRelevancyChecker.computeSalienceOfName(anyString(), anyString()))
        .thenReturn(RelevancyChecker.SALIENCE_THRESHOLD + 1.0);
    when(mockRelevancyChecker.isRelevant(anyObject(), anyString()))
        .thenCallRealMethod();
    assertThat(mockRelevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME)).isTrue();
    assertThat(mockRelevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME)).isTrue();
  }

  @Test
  public void computeSalienceOfName_findsEverythingIrrelevant() {
    // Check content relevancy with a mock relevancy checker that always computes a salience
    // score lower than {@code SALIENCE_THRESHOLD}.
    NewsArticle relevantNewsArticle = new NewsArticle(URL, null, null, PRIORITY);
    relevantNewsArticle.setContent(RELEVANT_CONTENT);
    NewsArticle irrelevantNewsArticle = new NewsArticle(URL, null, null, PRIORITY);
    irrelevantNewsArticle.setContent(IRRELEVANT_CONTENT);
    when(mockRelevancyChecker.computeSalienceOfName(anyString(), anyString()))
        .thenReturn(RelevancyChecker.SALIENCE_THRESHOLD - 1.0);
    when(mockRelevancyChecker.isRelevant(anyObject(), anyString()))
        .thenCallRealMethod();
    assertThat(mockRelevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME)).isFalse();
    assertThat(mockRelevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME)).isFalse();
  }

  // @TODO [Write tests that mocks {@code languageServiceClient.analyzeEntities()} and other
  // relevant methods of the Natural Language API.]
}
