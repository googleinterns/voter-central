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
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.mockito.Mockito.*;

/**
 * A tester for web scrawler's news article compilation process, excluding content extraction,
 * content processing and relevancy checking which are defined in standalone classes.
 */
@RunWith(JUnit4.class)
public final class WebCrawlerTest {
  private static final String CANDIDATE_NAME = "Alexandria Ocasio-Cortez";
  private static final String CANDIDATE_ID = "1";
  private static final String VALID_PROTOCOL = "https";
  private static final String INVALID_PROTOCOL = "htttttps";
  private static final String VALID_HOST = "www.cnn.com";
  private static final String INVALID_HOST = "www.cnn.cooooom";
  private static final String VALID_URL =
    "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";
  private static final String VALID_URL_ROBOTS_TXT =
    "https://www.cnn.com/robots.txt";
  private static final NewsArticle EXPECTED_NEWS_ARTICLE =
    new NewsArticle(
        "AOC wins NY Democratic primary against Michelle Caruso-Cabrera, CNN projects - " +
        "CNNPolitics",
        VALID_URL,
        "Washington (CNN)Freshman Democratic Rep. Alexandria Ocasio-Cortez will defeat former " +
        "longtime CNBC correspondent and anchor Michelle Caruso-Cabrera in a Democratic " +
        "primary election on Tuesday for New York's 14th Congressional District, CNN projects.");
  private static final String EMPTY_ABBREVIATED_CONTENT = "";
  private static final int DELAY = 1;

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

  // For the following two methods:
  // Mocking {@code URL}, which is a final class requires additional Mockito configuration.
  // @TODO [Might try the configuration. Alternatively might wrap URL, and mock the wrapper class.]
  //
  // @Test
  // public void scrapeAndExtractHtml_invalidProtocolUrl() throws IOException {
  //   // Scrape and extract news article content from an invalid {@code URL} that returns an invalid
  //   // protocol. The invalid {@code URL} would cause an {@code IOException} getting read. This
  //   // exception should be caught and an empty {@code Optional} should be returned.
  //   // Assume that the libraries {@code URL} and {@code RobotsTxt} work as intended.
  //   // Since Mockito doesn't support the mocking of static methods, {@code NewsContentExtractor}'s 
  //   // {@code extractContentFromHtml()} is not insular to this "unit" test. @TODO [Might modify
  //   // {@code NewsContentExtractor} to aid test-driven development.
  //   URL url = mock(URL.class);
  //   when(url.getProtocol()).thenReturn(INVALID_PROTOCOL);
  //   when(url.getHost()).thenReturn(VALID_HOST);
  //   Optional<NewsArticle> potentialNewsArticle = webCrawler.scrapeAndExtractFromHtml(url);
  //   Assert.assertFalse(potentialNewsArticle.isPresent());
  // }
  // @Test
  // public void scrapeAndExtractHtml_invalidHostUrl() throws IOException {
  //   // Scrape and extract news article content from an invalid {@code URL} that returns an invalid
  //   // host. The invalid {@code URL} would cause an {@code IOException} getting read. This
  //   // exception should be caught and an empty {@code Optional} should be returned.
  //   // Assume that the libraries {@code URL} and {@code RobotsTxt} work as intended.
  //   // Since Mockito doesn't support the mocking of static methods, {@code NewsContentExtractor}'s 
  //   // {@code extractContentFromHtml()} is not insular to this "unit" test. @TODO [Might modify
  //   // {@code NewsContentExtractor} to aid test-driven development.
  //   URL url = mock(URL.class);
  //   when(url.getProtocol()).thenReturn(VALID_PROTOCOL);
  //   when(url.getHost()).thenReturn(INVALID_HOST);
  //   Optional<NewsArticle> potentialNewsArticle = webCrawler.scrapeAndExtractFromHtml(url);
  //   Assert.assertFalse(potentialNewsArticle.isPresent());
  // }

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
    Assert.assertFalse(potentialNewsArticle.isPresent());
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
    Assert.assertTrue(potentialNewsArticle.isPresent());
    Assert.assertEquals(EXPECTED_NEWS_ARTICLE, potentialNewsArticle.get());
    Assert.assertTrue(webCrawler.getNextAccessTimes().containsKey(VALID_URL_ROBOTS_TXT));
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
    Assert.assertTrue(queryResult.hasNext());
    Entity newsArticleEntity = queryResult.next();
    Assert.assertFalse(queryResult.hasNext());
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
    Assert.assertEquals(newsArticleKey, newsArticleEntity.getKey());
    Assert.assertEquals(candidateKey, newsArticleEntity.getKey("candidateId"));
    Assert.assertEquals(EXPECTED_NEWS_ARTICLE.getTitle(), newsArticleEntity.getString("title"));
    Assert.assertEquals(EXPECTED_NEWS_ARTICLE.getUrl(), newsArticleEntity.getString("url"));
    Assert.assertEquals(EXPECTED_NEWS_ARTICLE.getContent(), newsArticleEntity.getString("content"));
    Assert.assertEquals(EMPTY_ABBREVIATED_CONTENT, newsArticleEntity.getString("abbreviatedContent"));
  }

  @AfterClass
  public static void cleanup() throws InterruptedException, IOException, TimeoutException {
    datastoreHelper.stop();
  }
}
