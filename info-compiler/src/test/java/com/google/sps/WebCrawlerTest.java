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
import com.google.cloud.datastore.FullEntity;
import com.google.cloud.datastore.IncompleteKey;
import com.google.cloud.datastore.Value;
import com.google.sps.data.NewsArticle;
import com.panforge.robotstxt.Grant;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
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
  private final static String CANDIDATE_NAME = "Alexandria Ocasio-Cortez";
  private final static String CANDIDATE_ID = "1";
  private final static String VALID_PROTOCOL = "https";
  private final static String INVALID_PROTOCOL = "htttttps";
  private final static String VALID_HOST = "www.cnn.com";
  private final static String INVALID_HOST = "www.cnn.cooooom";
  private final static String VALID_URL =
    "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";
  private final static String VALID_URL_ROBOTS_TXT =
    "https://www.cnn.com/robots.txt";
  private final static NewsArticle NEWS_ARTICLE =
    new NewsArticle(
        "AOC wins NY Democratic primary against Michelle Caruso-Cabrera, CNN projects - " +
        "CNNPolitics",
        VALID_URL,
        "Washington (CNN)Freshman Democratic Rep. Alexandria Ocasio-Cortez will defeat former " +
        "longtime CNBC correspondent and anchor Michelle Caruso-Cabrera in a Democratic " +
        "primary election on Tuesday for New York's 14th Congressional District, CNN projects.");
  private final static String ABBREVIATED_CONTENT = ".";
  private final static int DELAY = 1;

  private WebCrawler webCrawler;
  private Datastore datastore;

  @Before
  public void initialize() throws IOException {
    this.datastore = mock(Datastore.class);
    this.webCrawler = new WebCrawler(datastore);
    NEWS_ARTICLE.setAbbreviatedContent(ABBREVIATED_CONTENT);
  }

  // @TODO [Implement unit tests for getting URLs from the Custom Search engine.]
  @Test
  public void getUrlsFromCustomSearch() {}

  @Test
  public void scrapeAndExtractHtml_validUrl() throws IOException {
    // Scrape and extract news article content from {@code VALID_URL}. The content and metadata
    // packaged in {@code NewsArticle} should be consistent with that in {@code NEWS_ARTICLE}.
    // Assume that the libraries {@code URL} and {@code RobotsTxt} work as intended.
    // Since Mockito doesn't support the mocking of static methods, {@code NewsContentExtractor}'s 
    // {@code extractContentFromHtml()} is not insular to this "unit" test. @TODO [Might modify
    // {@code NewsContentExtractor} to aid test-driven development.
    URL url = new URL(VALID_URL);
    Optional<NewsArticle> potentialNewsArticle = webCrawler.scrapeAndExtractHtml(url);
    Assert.assertTrue(potentialNewsArticle.isPresent());
    NewsArticle newsArticle = potentialNewsArticle.get();
    Assert.assertEquals(newsArticle.getTitle(), NEWS_ARTICLE.getTitle());
    Assert.assertEquals(newsArticle.getUrl(), NEWS_ARTICLE.getUrl());
    Assert.assertTrue(newsArticle.getContent().contains(NEWS_ARTICLE.getContent()));
    Assert.assertNull(newsArticle.getAbbreviatedContent());
  }

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
  //   Optional<NewsArticle> potentialNewsArticle = webCrawler.scrapeAndExtractHtml(url);
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
  //   Optional<NewsArticle> potentialNewsArticle = webCrawler.scrapeAndExtractHtml(url);
  //   Assert.assertFalse(potentialNewsArticle.isPresent());
  // }

  @Test
  public void scrapeAndExtractHtml_nonscrapableWebpage() throws IOException {
    // Scrape and extract news article content from a non-scrapable webpage, as suggested by a mock
    // {@code Grant}. The URLs for robots.txt and the webpage are irrelevant and set to those
    // corresponding to {@code VALID_URL}. An empty {@code Optional} should be returned as the
    // result.
    // Since Mockito doesn't support the mocking of static methods, {@code NewsContentExtractor}'s 
    // {@code extractContentFromHtml()} is not insular to this "unit" test. @TODO [Might modify
    // {@code NewsContentExtractor} to aid test-driven development.
    URL url = new URL(VALID_URL);
    URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
    Grant grant = mock(Grant.class);
    when(grant.hasAccess()).thenReturn(false);
    Optional<NewsArticle> potentialNewsArticle = webCrawler.politelyScrapeAndExtractHtml(
        grant, robotsUrl, url);
    Assert.assertFalse(potentialNewsArticle.isPresent());
  }

  @Test
  public void scrapeAndExtractHtml_webpageThatRequiresDelay() throws IOException {
    // Scrape and extract news article content with required crawler delay, achieved through a mock
    // {@code Grant}. The required delay should be documented in {@code nextAccessTimes}. It is 
    // keyed by {@code VALID_URL_ROBOTS_TXT}, which is the robots.txt file corresponding to
    // {@code VALID_URL}. The extracted content and metadata in {@code newsArticle} should be
    // consistent with those of {@code NEWS_ARTICLE}.
    // Since Mockito doesn't support the mocking of static methods, {@code NewsContentExtractor}'s 
    // {@code extractContentFromHtml()} is not insular to this "unit" test. @TODO [Might modify
    // {@code NewsContentExtractor} to aid test-driven development.
    URL url = new URL(VALID_URL);
    URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
    Grant grant = mock(Grant.class);
    when(grant.hasAccess()).thenReturn(true);
    when(grant.getCrawlDelay()).thenReturn(DELAY);
    WebCrawler webCrawlerSpy = spy(webCrawler);
    Optional<NewsArticle> potentialNewsArticle = webCrawler.politelyScrapeAndExtractHtml(
        grant, robotsUrl, url);
    //verify(webCrawlerSpy, times(1)).waitForAndSetCrawlDelay(anyObject(), anyString());
    Assert.assertTrue(potentialNewsArticle.isPresent());
    NewsArticle newsArticle = potentialNewsArticle.get();
    Assert.assertEquals(newsArticle.getTitle(), NEWS_ARTICLE.getTitle());
    Assert.assertEquals(newsArticle.getUrl(), NEWS_ARTICLE.getUrl());
    Assert.assertTrue(newsArticle.getContent().contains(NEWS_ARTICLE.getContent()));
    Assert.assertNull(newsArticle.getAbbreviatedContent());
    Assert.assertTrue(webCrawler.getNextAccessTimes().containsKey(VALID_URL_ROBOTS_TXT));
  }

  @Test
  public void storeInDatabase_checkDatastoreEntityConstructionFromNewsArticle() {
    // Check that the Datastore service extracts the correct information from {@code NEWS_ARTICLE}
    // and construct the correct entity for storing that information.
    // Mocking {@code Key}, a final class, requires additional Mockito configuration. @TODO [Might
    // try the configuration.]
    // {@code ImcompleteKey} is used instead temporarily.
    // {@code IncompleteKey} cannot be used to construct {@code Entity}, so {@code FullEntity} is
    // used instead.
    IncompleteKey newsArticleKey = mock(IncompleteKey.class);
    FullEntity newsArticleEntity = webCrawler.storeInDatabase(CANDIDATE_ID, NEWS_ARTICLE,
                                                              newsArticleKey);
    Assert.assertEquals(newsArticleEntity.getString("title"), NEWS_ARTICLE.getTitle());
    Assert.assertEquals(newsArticleEntity.getString("url"), NEWS_ARTICLE.getUrl());
    Assert.assertEquals(newsArticleEntity.getString("content"), NEWS_ARTICLE.getContent());
    Assert.assertEquals(newsArticleEntity.getString("abbreviatedContent"),
                        NEWS_ARTICLE.getAbbreviatedContent());
  }

  // {@code newKeyFactory()} is deemed an abtract method and thus cannot be mocked directly or
  // invoked as a real method. Thus, that method will return null and subsequently cause a
  // {@code NullPointerException}. Up to now, have not found a mock-able alternative method that
  // realizes the same functionality as {@code newKeyFactory()}.
  //
  // @Test
  // public void storeInDatabase_verifyDatastoreOperations() {
  //   // Verify that the Datastore service create two keys, one for the news article to be inserted
  //   // and the other for constructing a foreign reference key to the corresponding candidate.
  //   // Verify that the Datastore service stores an entity in the database.
  //   webCrawler.storeInDatabase(CANDIDATE_ID, NEWS_ARTICLE);
  //   verify(datastore, times(2)).newKeyFactory();
  //   verify(datastore, times(1)).put(any(Entity.class));
  // }

  // Integrated test.
  //
  // @Test
  // public void compileNewsArticles_oneCandidate() {
  //   // Execute the entire news articles compilation process, starting from getting URLs about
  //   // {@code CANDIDATE_NAME}, and ending with storing processed news articles content in the
  //   // database.
  //   try {
  //     myWebCrawler = new WebCrawler();
  //   } catch (IOException e) {
  //     System.out.println("[ERROR] " + e);
  //     return;
  //   }
  //   myWebCrawler.compileNewsArticle(CANDIDATE_NAME, CANDIDATE_ID);
  // }
}
