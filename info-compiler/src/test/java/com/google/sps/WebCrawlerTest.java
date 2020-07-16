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
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.testing.LocalDatastoreHelper;
import com.google.sps.data.NewsArticle;
import com.panforge.robotstxt.Grant;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A tester for web scrawler's news article compilation process, excluding content extraction,
 * content processing and relevancy checking which are defined in standalone classes.
 * (It's recommended to run WebCrawlerTest indenpendently, not together with other tests in the
 * package. There is instability with Datastore emulators, potentially due to HTTP communication.)
 */
@RunWith(JUnit4.class)
public final class WebCrawlerTest {
  private final static String CANDIDATE_NAME = "Alexandria Ocasio-Cortez";
  private final static String CANDIDATE_ID = "1";
  private final static String VALID_URL =
    "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";
  private final static String VALID_URL_ROBOTS_TXT =
    "https://www.cnn.com/robots.txt";
  private final static NewsArticle EXPECTED_NEWS_ARTICLE =
    new NewsArticle(
        "AOC wins NY Democratic primary against Michelle Caruso-Cabrera, CNN projects - " +
        "CNNPolitics",
        VALID_URL,
        "Washington (CNN)Freshman Democratic Rep. Alexandria Ocasio-Cortez will defeat former " +
        "longtime CNBC correspondent and anchor Michelle Caruso-Cabrera in a Democratic " +
        "primary election on Tuesday for New York's 14th Congressional District, CNN projects.");
  private final static String EMPTY_ABBREVIATED_CONTENT = "";
  private final static int DELAY = 1;

  private static WebCrawler webCrawler;
  private static LocalDatastoreHelper datastoreHelper;
  private static Datastore datastore;
  private static NewsContentExtractor newsContentExtractor;
  private static RelevancyChecker relevancyChecker;

  @BeforeClass
  public static void initialize() throws InterruptedException, IOException {
    datastoreHelper = LocalDatastoreHelper.create();
    datastoreHelper.start();
    datastore = datastoreHelper.getOptions().getService();
    newsContentExtractor = mock(NewsContentExtractor.class);
    relevancyChecker = mock(RelevancyChecker.class);
    webCrawler = new WebCrawler(datastore, newsContentExtractor, relevancyChecker);
  }

  /**
   * Resets the internal state of the Datastore emulator and then {@code datastore}. Also resets
   * {@code webCrawler}. We choose to reset, instead of creating/destroying the Datastore emulator
   * at each test is because {@code datastoreHelper.stop()} sometimes generates a {@code
   * java.net.ConnectException}, when making HTTP requests. Hence we try to limit the number of
   * times {@code datastoreHelper} is created/destroyed.
   */
  @Before
  public void resetDatastore() throws IOException {
    datastoreHelper.reset();
    datastore = datastoreHelper.getOptions().getService();
    webCrawler = new WebCrawler(datastore, newsContentExtractor, relevancyChecker);
  }

  // @TODO [Implement unit tests for getting URLs from the Custom Search engine.]
  @Test
  public void getUrlsFromCustomSearch() {}

  // @TODO [Write tests that test invalid URL protocols and invalid hosts, either by mocking
  // {@code URL}, which is a final class that requires additional Mockito configuration, or by
  // writing a wrapper class for URL.]

  @Test
  public void scrapeAndExtractHtml_nonscrapableWebpage() throws IOException {
    // Scrape and extract news article content from a non-scrapable webpage, as suggested by a mock
    // {@code Grant}. The URLs for robots.txt and the webpage are irrelevant and set to those
    // corresponding to {@code VALID_URL}. An empty {@code Optional} should be returned as the
    // result.
    URL url = new URL(VALID_URL);
    URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
    Grant grant = mock(Grant.class);
    when(grant.hasAccess()).thenReturn(false);
    Optional<NewsArticle> potentialNewsArticle =
        webCrawler.politelyScrapeAndExtractFromHtml(grant, robotsUrl, url);
    assertThat(potentialNewsArticle).isEmpty();
  }

  @Test
  public void scrapeAndExtractHtml_webpageThatRequiresDelay() throws IOException {
    // Scrape and extract news article content with required crawler delay, achieved through a mock
    // {@code Grant}. The required delay should be documented in {@code nextAccessTimes}. It is 
    // keyed by {@code VALID_URL_ROBOTS_TXT}, which is the robots.txt file corresponding to
    // {@code VALID_URL}. The extracted content and metadata in {@code newsArticle} should be
    // consistent with those of {@code EXPECTED_NEWS_ARTICLE}.
    URL url = new URL(VALID_URL);
    URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
    Grant grant = mock(Grant.class);
    when(grant.hasAccess()).thenReturn(true);
    when(grant.getCrawlDelay()).thenReturn(DELAY);
    when(newsContentExtractor.extractContentFromHtml(anyObject(), anyString()))
        .thenReturn(Optional.of(EXPECTED_NEWS_ARTICLE));
    Optional<NewsArticle> potentialNewsArticle =
        webCrawler.politelyScrapeAndExtractFromHtml(grant, robotsUrl, url);
    assertThat(potentialNewsArticle).hasValue(EXPECTED_NEWS_ARTICLE);
    assertThat(webCrawler.getNextAccessTimes()).containsKey(VALID_URL_ROBOTS_TXT);
  }

  @Test
  public void storeInDatabase_checkDatastoreEntityConstructionFromNewsArticle()
      throws IOException {
    // Check that the Datastore service extracts the correct information from {@code
    // EXPECTED_NEWS_ARTICLE}, constructs the correct key and entity for storing that information,
    // and successfully stores said entity into the database. Use a Datastore emulator to simulate
    // operations, as opposed to a Mockito mock of Datastore which does not provide mocking of all
    // required operations.
    webCrawler.storeInDatabase(CANDIDATE_ID, EXPECTED_NEWS_ARTICLE);
    Query<Entity> query =
        Query.newEntityQueryBuilder()
            .setKind("NewsArticle")
            .build();
    QueryResults<Entity> queryResult = datastore.run(query);
    assertThat(queryResult.hasNext()).isTrue();
    Entity newsArticleEntity = queryResult.next();
    assertThat(queryResult.hasNext()).isFalse();
    Key newsArticleKey =
        datastore
            .newKeyFactory()
            .setKind("NewsArticle")
            .newKey((long) EXPECTED_NEWS_ARTICLE.getUrl().hashCode());
    Key candidateKey =
        datastore
            .newKeyFactory()
            .setKind("Candidate")
            .newKey(CANDIDATE_ID);
    assertThat(newsArticleEntity.getKey()).isEqualTo(newsArticleKey);
    assertThat(newsArticleEntity.getKey("candidateId")).isEqualTo(candidateKey);
    assertThat(newsArticleEntity.getString("title")).isEqualTo(EXPECTED_NEWS_ARTICLE.getTitle());
    assertThat(newsArticleEntity.getString("url")).isEqualTo(EXPECTED_NEWS_ARTICLE.getUrl());
    assertThat(newsArticleEntity.getString("content")).isEqualTo(EXPECTED_NEWS_ARTICLE.getContent());
    assertThat(newsArticleEntity.getString("abbreviatedContent")).isEqualTo(EMPTY_ABBREVIATED_CONTENT);
  }

  @AfterClass
  public static void cleanup() throws InterruptedException, IOException, TimeoutException {
    datastoreHelper.stop();
  }
}
