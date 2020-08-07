// Copyright 2019 Google LLC
// Copyright 2016 Piotr Andzel
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
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.TimestampValue;
import com.google.cloud.Timestamp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.sps.data.NewsArticle;
import com.google.sps.infocompiler.Config;
import com.google.sps.infocompiler.InfoCompiler;
import com.google.sps.webcrawler.NewsContentExtractor;
import com.google.sps.webcrawler.NewsContentProcessor;
import com.google.sps.webcrawler.RelevancyChecker;
import com.panforge.robotstxt.Grant;
import com.panforge.robotstxt.RobotsTxt;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/** A web crawler for compiling candidate-specific news articles information. */
public class WebCrawler {
  static final String CUSTOM_SEARCH_URL_METATAG = "og:url";
  static final List<String> CUSTOM_SEARCH_PUBLISHER_METATAGS =
      Arrays.asList("article:publisher", "og:site_name", "twitter:app:name:googleplay",
                    "dc.source");
  static final List<String> CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS =
      Arrays.asList("article:published_time", "article:published", "datepublished", "og:pubdate",
                    "pubdate", "published", "article:modified_time", "article:modified",
                    "modified");
  private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS =
      Arrays.asList(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  public static final int CUSTOM_SEARCH_RESULT_COUNT = 10;
  private static final int URL_CONNECT_TIMEOUT_MILLISECONDS = 1000;
  private static final int URL_READ_TIMEOUT_MILLISECONDS = 1000;
  private static final int MAX_CRAWL_DELAY = 30 * 1000;
  private Datastore datastore;
  private NewsContentExtractor newsContentExtractor;
  private RelevancyChecker relevancyChecker;
  // Mappings of (website robots.txt URL, the next allowed time to access, in milliseconds) for
  // respecting the required crawl delay.
  private Map<String, Long> nextAccessTimes = new HashMap<>();

  /**
   * Constructs a {@code WebCrawler} instance.
   *
   * @throws IOException if {@code RelevancyChecker} instantiation fails, such as because of lack
   *     of permission to access required libraries.
   */
  public WebCrawler() throws IOException {
    this(DatastoreOptions.getDefaultInstance().getService(), new NewsContentExtractor(),
         new RelevancyChecker());
  }

  /** For testing purposes. */
  public WebCrawler(Datastore datastore, NewsContentExtractor newsContentExtractor,
      RelevancyChecker relevancyChecker) throws IOException {
    this.datastore = datastore;
    this.newsContentExtractor = newsContentExtractor;
    this.relevancyChecker = relevancyChecker;
  }

  /**
   * Compiles news articles for the candidate with the specified {@code candidateName} and
   * {@code candidateId}:
   * 1. Obtains news article URLs and metadata from Google Custom Search.
   * 2. Checks for permission to web-scrape.
   * 3. Web-scrapes if permitted.
   * 4. Extracts content from HTML structure.
   * 5. Checks content relevancy to the candidate of interest.
   * 6. Processes content.
   * 7. Stores processed content in the database.
   */
  public void compileNewsArticle(String candidateName, String candidateId, String partyName) {
    List<NewsArticle> newsArticles = getUrlsFromCustomSearch(candidateName);
    for (NewsArticle newsArticle : newsArticles) {
      scrapeAndExtractFromHtml(newsArticle);
      if (!relevancyChecker.isRelevant(newsArticle, candidateName, partyName)) {
        continue;
      }
      NewsContentProcessor.abbreviate(newsArticle);
      NewsContentProcessor.summarize(newsArticle);
      storeInDatabase(candidateId, newsArticle);
    }
  }

  // [Might adopt in {@code compileNewsArticle}.]
  /**
   * Controls the web scraping frequency by delaying the @{code WebCrawler} for the maximum amount
   * of time as required by webpages encountered so far. This method is for frequency-tuning
   * purposes only. {@code WebCrawler} will confirm that the required crawl delay is met, before
   * actually moving forward with the web-scraping.
   */
  // private void waitForMaxCrawlDelay() {
  //   if (nextAccessTimes.isEmpty()) {
  //     return;
  //   }
  //   long timeToDelay = Collections.max(nextAccessTimes.values()) - System.currentTimeMillis();
  //   timeToDelay = Math.min(MAX_CRAWL_DELAY, timeToDelay);
  //   try {
  //     TimeUnit.MILLISECONDS.sleep(timeToDelay);
  //   } catch (InterruptedException e) {}
  // }

  /**
   * Searches for {@code candidateName} on News.google using the Google Custom Search engine and
   * finds URLs and metadata of news articles. Returns an empty list if no valid URLs are found.
   *
   * @see <a href="https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/"
   *    + "http/examples/client/ClientWithResponseHandler.java">Code reference</a>
   */
  public List<NewsArticle> getUrlsFromCustomSearch(String candidateName) {
    String request =
        String.format(
            "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s",
            Config.CUSTOM_SEARCH_KEY, Config.CUSTOM_SEARCH_ENGINE_ID,
            URLEncoder.encode(candidateName));
    CloseableHttpClient httpClient = HttpClients.createDefault();
    try {
      HttpGet httpGet = new HttpGet(request);
      JsonObject json = InfoCompiler.requestHttpAndBuildJsonResponse(httpClient, httpGet);
      return extractUrlsAndMetadataFromCustomSearchJson(json);
    } catch (IOException e) {
      System.out.println("[ERROR] Error occurred with fetching URLs from Custom Search: " + e);
      return Arrays.asList();
    }
  }

  /**
   * Parses {@code json}, which is in Google Custom Search's JSON response format, and extracts
   * news articles' URLs (URLs from the source website, instead of from News.google) and metadata,
   * including the publisher and published date. Packages extracted data into {@code NewsArticle}.
   * Set news articles' priority based on the order in which they are returned by Custom Search.
   */
  List<NewsArticle> extractUrlsAndMetadataFromCustomSearchJson(JsonObject json) {
    List<NewsArticle> newsArticles = new ArrayList<>(CUSTOM_SEARCH_RESULT_COUNT);
    JsonArray searchResults = json.getAsJsonArray("items");
    if (searchResults == null) {
      return Arrays.asList();
    }
    int priority = 1;
    for (JsonElement result : searchResults) {
      JsonObject metadata;
      String url;
      try {
        metadata =
            (JsonObject)
                (((JsonObject) result)
                    .getAsJsonObject("pagemap")
                        .getAsJsonArray("metatags")
                            .get(0));
        url = extractUrlMetadata(metadata);
      } catch (NullPointerException e) {
        continue;
      }
      String publisher = extractPublisherMetadata(metadata);
      Date publishedDate = extractPublishedDateMetadata(metadata);
      newsArticles.add(new NewsArticle(url, publisher, publishedDate, priority));
      priority++;
    }
    return newsArticles;
  }

  /**
   * Extracts the URL from {@code metadata}. Throws an exception if the URL wasn't found,
   * because the news article content relies on the URL.
   *
   * @throws NullPointerException if the metatag {@code CUSTOM_SEARCH_URL_METATAG} doesn't
   *     exist.
   */
  private String extractUrlMetadata(JsonObject metadata) {
    return metadata.get(CUSTOM_SEARCH_URL_METATAG).getAsString();
  }

  /**
   * Extracts the publisher from {@code metadata} by examining in order
   * {@code CUSTOM_SEARCH_PUBLISHER_METATAGS}. Returns null if no matching metatags are found.
   */
  private String extractPublisherMetadata(JsonObject metadata) {
    for (String potentialPublisherMetatag : CUSTOM_SEARCH_PUBLISHER_METATAGS) {
      if (metadata.has(potentialPublisherMetatag)) {
        return metadata.get(potentialPublisherMetatag).getAsString();
      }
    }
    return null;
  }

  /**
   * Extracts the published date from {@code metadata} by examining in order
   * {@code CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS} and parsing the date string. Returns null if no
   * matching metatags are found or if no date strings could be parsed.
   */
  private Date extractPublishedDateMetadata(JsonObject metadata) {
    for (String potentialDateMetatag : CUSTOM_SEARCH_PUBLISHED_DATE_METATAGS) {
      if (!metadata.has(potentialDateMetatag)) {
        continue;
      }
      String date = metadata.get(potentialDateMetatag).getAsString();
      for (DateTimeFormatter potentialDateTimeFormatter : DATE_TIME_FORMATTERS) {
        try {
          return Date.from(Instant.from(potentialDateTimeFormatter.parse(date)));
        } catch (IllegalArgumentException | DateTimeException e) {}
      }
    }
    return null;
  }

  /**
   * Checks robots.txt for permission to web-scrape, scrapes webpage if permitted and extracts
   * textual content to put into {@code newsArticle}. Sets "content" to empty in the event of an
   * exception.
   */
  public void scrapeAndExtractFromHtml(NewsArticle newsArticle) {
    try {
      URL url = new URL(newsArticle.getUrl());
      URL robotsUrl = new URL(url.getProtocol(), url.getHost(), "/robots.txt");
      InputStream robotsTxtStream = setTimeoutAndOpenStream(robotsUrl);
      RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
      robotsTxtStream.close();
      String webpagePath = url.getPath();
      Grant grant = robotsTxt.ask("*", webpagePath);
      politelyScrapeAndExtractFromHtml(grant, robotsUrl, newsArticle);
    } catch (Exception e) {
      System.out.println("[ERROR] Error occured in scrapeAndExtractHtml(): " + e);
      newsArticle.setTitle("");
      newsArticle.setContent("");
    }
  }

  /**
   * Checks robots.txt for permission to web-scrape, scrapes webpage if permitted and extracts
   * textual content to put into {@code newsArticle}. Sets "content" to empty in the event of an
   * exception.
   */
  void politelyScrapeAndExtractFromHtml(Grant grant, URL robotsUrl,
      NewsArticle newsArticle) {
    try {
      // Check permission to access and respect the required crawl delay.
      if (grant == null
          || (grant.hasAccess()
              && waitForAndSetCrawlDelay(grant, robotsUrl.toString()))) {
        InputStream webpageStream = setTimeoutAndOpenStream(new URL(newsArticle.getUrl()));
        newsContentExtractor.extractContentFromHtml(webpageStream, newsArticle);
        webpageStream.close();
      } else {
        newsArticle.setTitle("");
        newsArticle.setContent("");
        return;
      }
    } catch (Exception e) {
      System.out.println("[ERROR] Error occured in politelyScrapeAndExtractHtml(): " + e);
      newsArticle.setTitle("");
      newsArticle.setContent("");
    }
  }

  /**
   * Opens a readable {@code InputStream} from {@code url}, while setting a connect and read
   * timeout so that opening stream wouldn't hang. Timeout will trigger exceptions.
   */
  private InputStream setTimeoutAndOpenStream(URL url) throws IOException {
    URLConnection connection = url.openConnection();
    connection.setConnectTimeout(URL_CONNECT_TIMEOUT_MILLISECONDS);
    connection.setReadTimeout(URL_READ_TIMEOUT_MILLISECONDS);
    return connection.getInputStream();
  }

  /**
   * Waits for the required crawl delay to pass if necessary and makes a note of the required crawl
   * delay. Returns true if the aforementioned process succeeded. {@code grant} is expected to be
   * non-null. This method is made default for testing purposes.
   */
  boolean waitForAndSetCrawlDelay(Grant grant, String url) {
    if (grant.getCrawlDelay() == null) {
      return true;
    }
    if (nextAccessTimes.containsKey(url)) {
      if (!waitIfNecessary(url)) {
        return false;
      }
      nextAccessTimes.replace(url, System.currentTimeMillis() + grant.getCrawlDelay() * 1000);
    } else {
      nextAccessTimes.put(url, System.currentTimeMillis() + grant.getCrawlDelay() * 1000);
    }
    return true;
  }

  /**
   * Waits for {@code timeToDelay} milliseconds if necessary and returns true if the pause
   * succeeded or if the pause was unnecessary. Waits for a maximum of {@code MAX_CRAWL_DELAY}
   * milliseconds.
   */
  private boolean waitIfNecessary(String url) {
    if (System.currentTimeMillis() < nextAccessTimes.get(url)) {
      try {
        long sleepDuration = nextAccessTimes.get(url) - System.currentTimeMillis();
        if (sleepDuration > MAX_CRAWL_DELAY) {
          return false;
        }
        TimeUnit.MILLISECONDS.sleep(sleepDuration);
      } catch (InterruptedException e) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stores {@code NewsArticle}'s metadata and content into the database, following a predesigned
   * database schema. Requires "gcloud config set project project-ID" to be set correctly. {@code
   * content} and {@code abbreviatedContent} are excluded form database indexes, which are
   * additional data structures built to enable efficient lookup on non-keyed properties. Because
   * we will not query {@code NewsArticle} Datastore entities via {@code content} or
   * {@code abbreviatedContent}, we will not use indexes regardless. Set the last modified time
   * for deletion purposes.
   */
  public void storeInDatabase(String candidateId, NewsArticle newsArticle) {
    Key newsArticleKey =
        datastore
            .newKeyFactory()
            .setKind("NewsArticle")
            .newKey((long) newsArticle.getUrl().hashCode());
    Entity newsArticleEntity =
        Entity.newBuilder(newsArticleKey)
            .set("candidateId", datastore.newKeyFactory()
                                    .setKind("Candidate")
                                    .newKey(Long.parseLong(candidateId)))
            .set("title", newsArticle.getTitle())
            .set("url", newsArticle.getUrl())
            .set("content", excludeStringFromIndexes(newsArticle.getContent()))
            .set(
                "abbreviatedContent", excludeStringFromIndexes(newsArticle.getAbbreviatedContent()))
            .set("summarizedContent", excludeStringFromIndexes(newsArticle.getSummarizedContent()))
            .set("publisher", newsArticle.getPublisher())
            .set("publishedDate", TimestampValue.newBuilder(
                                      Timestamp.of(
                                          newsArticle.getPublishedDate())).build())
            .set("priority", newsArticle.getPriority())
            .set("lastModified", Timestamp.now())
            .build();
    datastore.put(newsArticleEntity);
  }

  /**
   * Converts {@code String} to {@code StringValue} and excludes the data from indexes, to avoid
   * the 1500-byte size limit for indexed data.
   */
  private StringValue excludeStringFromIndexes(String content) {
    return StringValue
        .newBuilder(content == null ? "" : content)
        .setExcludeFromIndexes(true)
        .build();
  }

  /** For testing purposes. */
  Map<String, Long> getNextAccessTimes() {
    return this.nextAccessTimes;
  }
}
