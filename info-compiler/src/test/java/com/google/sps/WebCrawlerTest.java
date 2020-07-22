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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.sps.data.NewsArticle;
import com.panforge.robotstxt.Grant;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.List;
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
 * package that use Datastore emulators. There is instability with Datastore emulators, potentially
 * due to HTTP communication.)
 */
@RunWith(JUnit4.class)
public final class WebCrawlerTest {
  private static final String CANDIDATE_NAME = "Alexandria Ocasio-Cortez";
  private static final String CANDIDATE_ID = "1";
  private static final String VALID_URL =
      "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";
  private static final String VALID_URL_ROBOTS_TXT =
      "https://www.cnn.com/robots.txt";
  private static final String TITLE =
      "AOC wins NY Democratic primary against Michelle Caruso-Cabrera, CNN projects - CNNPolitics";
  private static final String CONTENT =
      "Washington (CNN)Freshman Democratic Rep. Alexandria Ocasio-Cortez will defeat former " +
      "longtime CNBC correspondent and anchor Michelle Caruso-Cabrera in a Democratic " +
      "primary election on Tuesday for New York's 14th Congressional District, CNN projects.";
  private static final String EMPTY_CONTENT = "";
  private static final String EMPTY_ABBREVIATED_CONTENT = "";
  private static final int DELAY = 1;

  private static WebCrawler webCrawler;
  private static LocalDatastoreHelper datastoreHelper;
  private static Datastore datastore;
  private static NewsContentExtractor newsContentExtractor;
  private static RelevancyChecker relevancyChecker;
  private static JsonObject customSearchJson;

  @BeforeClass
  public static void initialize() throws InterruptedException, IOException {
    datastoreHelper = LocalDatastoreHelper.create();
    datastoreHelper.start();
    datastore = datastoreHelper.getOptions().getService();
    newsContentExtractor = mock(NewsContentExtractor.class);
    relevancyChecker = mock(RelevancyChecker.class);
    webCrawler = new WebCrawler(datastore, newsContentExtractor, relevancyChecker);

    // {@code customSearchJson} has the following JSON structure (ellipsis represents other
    // properties not shown). Insert {@code VALID_URL}.
    // {
    //   ...
    //   "items": [
    //     {
    //       ...
    //       "pagemap": {
    //         ...
    //         "metatags": [
    //           {
    //             ...
    //             {@code WebCrawler.CUSTOM_SEARCH_URL_METATAG}: <URL>,
    //             ...
    //           }
    //         ]
    //         ...
    //       }
    //       ...
    //     }
    //   ]
    //   ...
    // }
    JsonObject urlMetadata = new JsonObject();
    urlMetadata.addProperty(WebCrawler.CUSTOM_SEARCH_URL_METATAG, VALID_URL);
    JsonArray metatags = new JsonArray();
    metatags.add(urlMetadata);
    JsonObject pagemap = new JsonObject();
    pagemap.add("metatags", metatags);
    JsonObject item = new JsonObject();
    item.add("pagemap", pagemap);
    JsonArray items = new JsonArray();
    items.add(item);
    customSearchJson = new JsonObject();
    customSearchJson.add("items", items);
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

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_regularJson() throws IOException {
    // Extract news article URL and metadata from {@code customSearchJson}, which is in format @see
    // {@code initialize()}.
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(customSearchJson);
    assertThat(newsArticles).containsExactly(new NewsArticle(VALID_URL, null, null));
  }

  // @TODO [Write tests that test invalid URL protocols and invalid hosts, either by mocking
  // {@code URL}, which is a final class that requires additional Mockito configuration, or by
  // writing a wrapper class for URL.]

  @Test
  public void scrapeAndExtractHtml_nonscrapableWebpage() throws IOException {
    // Scrape and extract news article content from a non-scrapable webpage, as suggested by a mock
    // {@code Grant}. The URLs for robots.txt and the webpage are irrelevant and set to those
    // corresponding to {@code VALID_URL}. An empty content should be set.
    URL url = new URL(VALID_URL);
    URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
    Grant grant = mock(Grant.class);
    when(grant.hasAccess()).thenReturn(false);
    NewsArticle newsArticle = new NewsArticle(url.toString(), null, null);
    webCrawler.politelyScrapeAndExtractFromHtml(grant, robotsUrl, newsArticle);
    assertThat(newsArticle.getContent()).isEqualTo(EMPTY_CONTENT);
  }

  @Test
  public void scrapeAndExtractHtml_webpageThatRequiresDelay() throws IOException {
    // Scrape and extract news article content with required crawler delay, achieved through a mock
    // {@code Grant}. The required delay should be documented in {@code nextAccessTimes}. It is 
    // keyed by {@code VALID_URL_ROBOTS_TXT}, which is the robots.txt file corresponding to
    // {@code VALID_URL}.
    URL url = new URL(VALID_URL);
    URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
    Grant grant = mock(Grant.class);
    when(grant.hasAccess()).thenReturn(true);
    when(grant.getCrawlDelay()).thenReturn(DELAY);
    NewsArticle newsArticle = new NewsArticle(url.toString(), null, null);
    webCrawler.politelyScrapeAndExtractFromHtml(grant, robotsUrl, newsArticle);
    assertThat(webCrawler.getNextAccessTimes()).containsKey(VALID_URL_ROBOTS_TXT);
  }

  @Test
  public void storeInDatabase_checkDatastoreEntityConstructionFromNewsArticle()
      throws IOException {
    // Check that the Datastore service extracts the correct information from {@code
    // expectedNewsArticle}, constructs the correct key and entity for storing that information,
    // and successfully stores said entity into the database. Use a Datastore emulator to simulate
    // operations, as opposed to a Mockito mock of Datastore which does not provide mocking of all
    // required operations.
    NewsArticle expectedNewsArticle = new NewsArticle(VALID_URL, null, null);
    expectedNewsArticle.setTitle(TITLE);
    expectedNewsArticle.setContent(CONTENT);
    webCrawler.storeInDatabase(CANDIDATE_ID, expectedNewsArticle);
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
            .newKey((long) expectedNewsArticle.getUrl().hashCode());
    Key candidateKey =
        datastore
            .newKeyFactory()
            .setKind("Candidate")
            .newKey(CANDIDATE_ID);
    assertThat(newsArticleEntity.getKey()).isEqualTo(newsArticleKey);
    assertThat(newsArticleEntity.getKey("candidateId")).isEqualTo(candidateKey);
    assertThat(newsArticleEntity.getString("title")).isEqualTo(expectedNewsArticle.getTitle());
    assertThat(newsArticleEntity.getString("url")).isEqualTo(expectedNewsArticle.getUrl());
    assertThat(newsArticleEntity.getString("content")).isEqualTo(expectedNewsArticle.getContent());
    assertThat(newsArticleEntity.getString("abbreviatedContent")).isEqualTo(EMPTY_ABBREVIATED_CONTENT);
  }

  @AfterClass
  public static void cleanup() throws InterruptedException, IOException, TimeoutException {
    datastoreHelper.stop();
  }
}
