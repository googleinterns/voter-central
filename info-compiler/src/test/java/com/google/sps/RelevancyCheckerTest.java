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

import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.sps.data.NewsArticle;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xml.sax.SAXException;
import static org.mockito.Mockito.*;

/**
 * A tester for the relevancy checker of news articles.
 */
@RunWith(JUnit4.class)
public final class RelevancyCheckerTest {
  private final static String TITLE = "title";
  private final static String URL = "https://www.cnn.com/index.html";
  private final static String RELEVANT_CONTENT = "Alexandria Ocasio-Cortez is A.O.C.";
  private final static String IRRELEVANT_CONTENT = "Irrelevant content.";
  private final static String CANDIDATE_NAME = "Alexandria Ocasio-Cortez";

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
    NewsArticle relevantNewsArticle = new NewsArticle(TITLE, URL, RELEVANT_CONTENT);
    NewsArticle irrelevantNewsArticle = new NewsArticle(TITLE, URL, IRRELEVANT_CONTENT);
    when(mockRelevancyChecker.computeSalienceOfName(anyString(), anyString()))
        .thenReturn(RelevancyChecker.SALIENCE_THRESHOLD + 1.0);
    when(mockRelevancyChecker.isRelevant(anyObject(), anyString()))
        .thenCallRealMethod();
    Assert.assertTrue(mockRelevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME));
    Assert.assertTrue(mockRelevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME));
  }

  @Test
  public void computeSalienceOfName_findsEverythingIrrelevant() {
    // Check content relevancy with a mock relevancy checker that always computes a salience
    // score lower than {@code SALIENCE_THRESHOLD}.
    NewsArticle relevantNewsArticle = new NewsArticle(TITLE, URL, RELEVANT_CONTENT);
    NewsArticle irrelevantNewsArticle = new NewsArticle(TITLE, URL, IRRELEVANT_CONTENT);
    when(mockRelevancyChecker.computeSalienceOfName(anyString(), anyString()))
        .thenReturn(RelevancyChecker.SALIENCE_THRESHOLD - 1.0);
    when(mockRelevancyChecker.isRelevant(anyObject(), anyString()))
        .thenCallRealMethod();
    Assert.assertFalse(mockRelevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME));
    Assert.assertFalse(mockRelevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME));
  }

  // @TODO [Write tests that mocks {@code languageServiceClient.analyzeEntities()} and other
  // relevant methods of the Natural Language API.]
}
