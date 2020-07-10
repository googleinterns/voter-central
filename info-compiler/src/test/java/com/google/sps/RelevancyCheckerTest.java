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

  private AnalyzeEntitiesResponse analyzeEntitiesResponse;
  private Entity entity;
  private LanguageServiceClient languageServiceClient;
  private RelevancyChecker mockRelevancyChecker;
  private RelevancyChecker relevancyChecker;
  private RelevancyChecker realRelevancyChecker;

  @Before
  public void initialize() throws IOException {
    mockRelevancyChecker = mock(RelevancyChecker.class);
    LanguageServiceClient languageServiceClient = mock(LanguageServiceClient.class);
    relevancyChecker = new RelevancyChecker(languageServiceClient);
    realRelevancyChecker = new RelevancyChecker();
  }

  @Test
  public void computeSalienceOfName_checkWithMockComputationThatFindsEverythingRelevant() {
    // Check content relevancy with a mock relevancy checker that always computes a salience
    // score higher than {@code SALIENCE_THRESHOLD}. The mock will determine relevancy by
    // comparing the fake salience score with {@code SALIENCE_THRESHOLD} as a real relevancy
    // checker would. As a result, both {@code RELEVANT_CONTENT} and {@code IRRELEVANT_CONTENT}
    // will be deemed relevant.
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
  public void computeSalienceOfName_checkWithMockComputationThatFindsEverythingIrrelevant() {
    // Check content relevancy with a mock relevancy checker that always computes a salience
    // score lower than {@code SALIENCE_THRESHOLD}. The mock will determine relevancy by
    // comparing the fake salience score with {@code SALIENCE_THRESHOLD} as a real relevancy
    // checker would. As a result, both {@code RELEVANT_CONTENT} and {@code IRRELEVANT_CONTENT}
    // will be deemed irrelevant.
    NewsArticle relevantNewsArticle = new NewsArticle(TITLE, URL, RELEVANT_CONTENT);
    NewsArticle irrelevantNewsArticle = new NewsArticle(TITLE, URL, IRRELEVANT_CONTENT);
    when(mockRelevancyChecker.computeSalienceOfName(anyString(), anyString()))
        .thenReturn(RelevancyChecker.SALIENCE_THRESHOLD - 1.0);
    when(mockRelevancyChecker.isRelevant(anyObject(), anyString()))
        .thenCallRealMethod();
    Assert.assertFalse(mockRelevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME));
    Assert.assertFalse(mockRelevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME));
  }

  // The following two methods attempt mock {@code languageServiceClient.analyzeEntities()} and
  // other associated methods of the Natural Language API. However, there are a series of
  // obstacles that cannot be resolved by solutions suggested by the official documentation, online
  // forums, or by different versions of Mockito (1-3) or different Mockito classes:
  // 1. analyzeEntities() is an ambiguous method (analyzeEntities(Document) and
  // analyzeEntities(AnalyzeEntitiesRequest). Thus, directly executing the following doesn't work:
  // {@code when(languageServiceClient.analyzeEntities()).thenReturn(analyzeEntitiesResponse);}.
  // 2. Theoretically, this ambiguity should be resolved by specifying the type/class of the
  // argument: {@code analyzeEntities(any(AnalyzeEntitiesRequest.class))}. However, this doesn't
  // work under Mockito versions 1-3, or with either {@code Mockito.any()} or
  // {@code ArgumentMatchers.any()}. A {@code NullPointerException} is thrown, perhaps due to
  // @see https://stackoverflow.com/questions/36272751/mockito-nullpointerexception-while-using-any.
  // {@code any()} seems to work under other contexts, such as {@code verify()}.
  // 3. To get around with {@code any()} or other argument matchers, we tried constructing an
  // instance of AnalyzeEntitiesRequest and passing that to {@code analyzeEntities(request)}.
  // However, this also throws a {@code NullInsteadOfMockException}.
  // Because of the situation mentioned above, we currently directly mock
  // {@code computeSalienceOfName()} in the two tests above, instead of mocking the sequence of
  // method calls within {@code computeSalienceOfName()}.
  //
  // @Test
  // public void computeSalienceOfName_checkWithMockClientThatFindsEverythingRelevant() {
  //   // Check content relevancy with a mock Natural Language API client that computes a salience
  //   // score higher than {@code SALIENCE_THRESHOLD}. Both {@code RELEVANT_CONTENT} and
  //   // {@code IRRELEVANT_CONTENT} will be deemed relevant. {@code thenReturn} does not take
  //   // double parameters so we cast salience to float.
  //   NewsArticle relevantNewsArticle = new NewsArticle(TITLE, URL, RELEVANT_CONTENT);
  //   Document doc = Document.newBuilder()
  //       .setContent(RELEVANT_CONTENT)
  //       .setType(Type.PLAIN_TEXT)
  //       .build();
  //   AnalyzeEntitiesRequest request =
  //       AnalyzeEntitiesRequest.newBuilder()
  //           .setDocument(doc)
  //           .setEncodingType(EncodingType.UTF8)
  //           .build();
  //   doReturn(analyzeEntitiesResponse).when(languageServiceClient).analyzeEntities(request);
  //   when(analyzeEntitiesResponse.getEntitiesList()).thenReturn(Arrays.asList(entity));
  //   when(entity.getSalience()).thenReturn((float) (RelevancyChecker.SALIENCE_THRESHOLD + 1));
  //   Assert.assertTrue(relevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME));
  //   NewsArticle irrelevantNewsArticle = new NewsArticle(TITLE, URL, IRRELEVANT_CONTENT);
  //   // Perform the same thing for {@code IRRELEVANT_CONTENT}.
  //   Assert.assertTrue(relevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME));
  // }
  // @Test
  // public void computeSalienceOfName_checkWithMockClientThatFindsEverythingIrrelevant() {
  //   // Check content relevancy with a mock Natural Language API client that computes a salience
  //   // score lower than {@code SALIENCE_THRESHOLD}. Both {@code RELEVANT_CONTENT} and
  //   // {@code IRRELEVANT_CONTENT} will be deemed irrelevant. {@code thenReturn} does not take
  //   // double parameters so we cast salience to float.
  //   NewsArticle relevantNewsArticle = new NewsArticle(TITLE, URL, RELEVANT_CONTENT);
  //   NewsArticle irrelevantNewsArticle = new NewsArticle(TITLE, URL, IRRELEVANT_CONTENT);
  //   when(languageServiceClient.analyzeEntities(any(AnalyzeEntitiesRequest.class)))
  //       .thenReturn(analyzeEntitiesResponse);
  //   when(analyzeEntitiesResponse.getEntitiesList()).thenReturn(Arrays.asList(entity));
  //   when(entity.getSalience()).thenReturn((float) (RelevancyChecker.SALIENCE_THRESHOLD - 1));
  //   Assert.assertFalse(relevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME));
  //   Assert.assertFalse(relevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME));
  // }

  // The client library cannot be authenticated for Maven test:
  // {@code com.google.api.gax.rpc.PermissionDeniedException}.
  //
  // @Test
  // public void isRelevant_checkWithRealClient() {
  //   // Check content relevancy with a Natural Language API client. {@code RELEVANT_CONTENT} will
  //   // be deemed relevant, and {@code IRRELEVANT_CONTENT} irrelevant.
  //   NewsArticle relevantNewsArticle = new NewsArticle(TITLE, URL, RELEVANT_CONTENT);
  //   NewsArticle irrelevantNewsArticle = new NewsArticle(TITLE, URL, IRRELEVANT_CONTENT);
  //   Assert.assertTrue(realRelevancyChecker.isRelevant(relevantNewsArticle, CANDIDATE_NAME));
  //   Assert.assertFalse(realRelevancyChecker.isRelevant(irrelevantNewsArticle, CANDIDATE_NAME));
  // }
}
