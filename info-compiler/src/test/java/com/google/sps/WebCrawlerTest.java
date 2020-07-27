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
import java.util.Date;
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
  private static final String PUBLISHER = "CNN";
  private static final String WRONG_PUBLISHER = "Wrong Publisher";
  // Jul 22, 2020 10:30:00 UTC (1595413800000 milliseconds since Jan 1, 1970, 00:00:00 GMT).
  private static final String PUBLISHED_DATE_FORMAT1 = "2020-07-22T10:30:00.000Z";
  private static final String PUBLISHED_DATE_FORMAT2 = "2020-07-22T10:30:00+0000";
  private static final String PUBLISHED_DATE_FORMAT3 = "2020-07-22T10:30:00+00:00";
  private static final String PUBLISHED_DATE_FORMAT4 = "2020-07-22T10:30:00Z";
  private static final String WRONG_PUBLISHED_DATE = "1970-01-01T00:00:00+000Z";
  private static final String UNPARSEABLE_PUBLISHED_DATE = "2020/07/22T10:30:00Z";
  private static final Date PUBLISHED_DATE = new Date(1595413800000L);
  private static final String TITLE =
      "AOC wins NY Democratic primary against Michelle Caruso-Cabrera, CNN projects - CNNPolitics";
  private static final String CONTENT =
      "Washington (CNN)Freshman Democratic Rep. Alexandria Ocasio-Cortez will defeat former " +
      "longtime CNBC correspondent and anchor Michelle Caruso-Cabrera in a Democratic " +
      "primary election on Tuesday for New York's 14th Congressional District, CNN projects.";
  private static final String EMPTY_CONTENT = "";
  private static final String EMPTY_ABBREVIATED_CONTENT = "";
  private static final String EMPTY_SUMMARIZED_CONTENT = "";
  private static final int PRIORITY = 1;
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
    //             one or more {@code WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS}:
    //                 <publisher>,
    //             one or more {@code WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS}:
    //                 <publishedDate>,
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
    JsonArray items = new JsonArray();
    items.add(constructABasicNewsArticle());
    customSearchJson = new JsonObject();
    customSearchJson.add("items", items);
  }

  /**
   * Constructs the JSON structure of a news article in Custom Search's response.
   */
  private static JsonObject constructABasicNewsArticle() {
    JsonObject metadata = new JsonObject();
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_URL_METATAG, VALID_URL);
    JsonArray metatags = new JsonArray();
    metatags.add(metadata);
    JsonObject pagemap = new JsonObject();
    pagemap.add("metatags", metatags);
    JsonObject item = new JsonObject();
    item.add("pagemap", pagemap);
    return item;
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
  public void extractUrlsAndMetadataFromCustomSearchJson_regularJsonWithDateFormat1()
      throws IOException {
    // Extract news article URL and metadata from {@code regularJson}, whose structure is shown in
    // {@code initialize()} and added with the news article URL, publisher and published date
    // (formatted as {@code PUBLISHED_DATE_FORMAT1}).
    // @see #initialize()
    JsonObject regularJson = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(regularJson);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         PUBLISHED_DATE_FORMAT1);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(regularJson);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, PUBLISHED_DATE, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_regularJsonWithDateFormat2()
      throws IOException {
    // Extract news article URL and metadata from {@code regularJson}, whose structure is shown in
    // {@code initialize()} and added with the news article URL, publisher and published date (in
    // (formatted as {@code PUBLISHED_DATE_FORMAT2}).
    // @see #initialize()
    JsonObject regularJson = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(regularJson);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         PUBLISHED_DATE_FORMAT2);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(regularJson);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, PUBLISHED_DATE, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_regularJsonWithDateFormat3()
      throws IOException {
    // Extract news article URL and metadata from {@code regularJson}, whose structure is shown in
    // {@code initialize()} and added with the news article URL, publisher and published date (in
    // (formatted as {@code PUBLISHED_DATE_FORMAT3}).
    // @see #initialize()
    JsonObject regularJson = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(regularJson);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         PUBLISHED_DATE_FORMAT3);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(regularJson);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, PUBLISHED_DATE, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_regularJsonWithDateFormat4()
      throws IOException {
    // Extract news article URL and metadata from {@code regularJson}, whose structure is shown in
    // {@code initialize()} and added with the news article URL, publisher and published date (in
    // (formatted as {@code PUBLISHED_DATE_FORMAT4}).
    // @see #initialize()
    JsonObject regularJson = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(regularJson);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         PUBLISHED_DATE_FORMAT4);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(regularJson);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, PUBLISHED_DATE, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_jsonWithTwoPublishersOfDifferentPriority()
      throws IOException {
    // Extract news article URL and metadata from {@code jsonWithTwoPublishers}, which contains
    // two publisher data marked under two different metatags. Because the order of the metatags in
    // {@code WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS} represents their priority, the publisher
    // marked under the earlier metatag should be extracted, namely {@code PUBLISHER}, instead of
    // {@code WRONG_PUBLISHER}.
    JsonObject jsonWithTwoPublishers = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(jsonWithTwoPublishers);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(1), WRONG_PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         PUBLISHED_DATE_FORMAT1);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(jsonWithTwoPublishers);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, PUBLISHED_DATE, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_jsonWithTwoDatesOfDifferentPriority()
      throws IOException {
    // Extract news article URL and metadata from {@code jsonWithTwoDates}, which contains two
    // published dates data marked under two different metatags. Because the order of the metatags
    // in {@code WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS} represents their priority, the
    // published date marked under the earlier metatag should be extracted, namely
    // {@code PUBLISHED_DATE}, instead of the date represented by {@code WRONG_PUBLISHED_DATE}.
    JsonObject jsonWithTwoDates = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(jsonWithTwoDates);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         PUBLISHED_DATE_FORMAT1);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(1),
                         WRONG_PUBLISHED_DATE);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(jsonWithTwoDates);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, PUBLISHED_DATE, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_jsonWithUnparseableDate()
      throws IOException {
    // Extract news article URL and metadata from {@code jsonWithUnparseableDate}, which contains
    // an unparseable date string {@code UNPARSEABLE_PUBLISHED_DATE}.
    JsonObject jsonWithUnparseableDate = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(jsonWithUnparseableDate);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHER_METATAGS.get(0), PUBLISHER);
    metadata.addProperty(WebCrawler.CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS.get(0),
                         UNPARSEABLE_PUBLISHED_DATE);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(jsonWithUnparseableDate);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, PUBLISHER, null, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_jsonWithoutMetadata()
      throws IOException {
    // Extract news article URL and metadata from {@code customSearchJson}, which contains no
    // publisher or published date metadata.
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(customSearchJson);
    assertThat(newsArticles).containsExactly(new NewsArticle(VALID_URL, null, null, PRIORITY));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_jsonWithoutTwoArticles()
      throws IOException {
    // Extract news article URL and metadata from {@code jsonWithTwoArticles}, which contains two
    // news articles. These news articles should be assigned {@code PRIORITY} and {@code
    // PRIORITY + 1} respectively.
    JsonObject jsonWithTwoArticles = customSearchJson.deepCopy();
    jsonWithTwoArticles.getAsJsonArray("items").add(constructABasicNewsArticle());
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(jsonWithTwoArticles);
    assertThat(newsArticles).containsExactly(
        new NewsArticle(VALID_URL, null, null, PRIORITY),
        new NewsArticle(VALID_URL, null, null, PRIORITY + 1));
  }

  @Test
  public void extractUrlsAndMetadataFromCustomSearchJson_jsonWithoutUrl()
      throws IOException {
    // Extract news article URL and metadata from {@code jsonWithoutUrl}, which contains no
    // URL. No news article should be returned.
    JsonObject jsonWithoutUrl = customSearchJson.deepCopy();
    JsonObject metadata = getMetadataJsonObject(jsonWithoutUrl);
    metadata.remove(WebCrawler.CUSTOM_SEARCH_URL_METATAG);
    List<NewsArticle> newsArticles =
        webCrawler.extractUrlsAndMetadataFromCustomSearchJson(jsonWithoutUrl);
    assertThat(newsArticles).isEmpty();
  }

  /**
   * Obtains the innermost-level metadata from the complete {@code customSearchJson}.
   */
  private JsonObject getMetadataJsonObject(JsonObject customSearchJson) {
    return (JsonObject)
        ((JsonObject) customSearchJson.getAsJsonArray("items").get(0))
            .getAsJsonObject("pagemap")
                .getAsJsonArray("metatags").get(0);
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
    NewsArticle newsArticle = new NewsArticle(url.toString(), null, null, PRIORITY);
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
    NewsArticle newsArticle = new NewsArticle(url.toString(), null, null, PRIORITY);
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
    NewsArticle expectedNewsArticle = new NewsArticle(VALID_URL, null, null, PRIORITY);
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
            .newKey(Long.parseLong(CANDIDATE_ID));
    assertThat(newsArticleEntity.getKey()).isEqualTo(newsArticleKey);
    assertThat(newsArticleEntity.getKey("candidateId")).isEqualTo(candidateKey);
    assertThat(newsArticleEntity.getString("title")).isEqualTo(expectedNewsArticle.getTitle());
    assertThat(newsArticleEntity.getString("url")).isEqualTo(expectedNewsArticle.getUrl());
    assertThat(newsArticleEntity.getString("content")).isEqualTo(expectedNewsArticle.getContent());
    assertThat(newsArticleEntity.getString("abbreviatedContent")).isEqualTo(EMPTY_ABBREVIATED_CONTENT);
    assertThat(newsArticleEntity.getString("abbreviatedContent")).isEqualTo(EMPTY_SUMMARIZED_CONTENT);
    assertThat(newsArticleEntity.getValue("priority").get()).isEqualTo(expectedNewsArticle.getPriority());
  }

  @AfterClass
  public static void cleanup() throws InterruptedException, IOException, TimeoutException {
    datastoreHelper.stop();
  }
}
