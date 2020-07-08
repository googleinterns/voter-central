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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.StringValue;
import com.google.gson.Gson;
import com.google.sps.data.NewsArticle;
import com.google.sps.webcrawler.NewsContentExtractor;
import com.google.sps.webcrawler.NewsContentProcessor;
import com.google.sps.webcrawler.RelevancyChecker;
import com.panforge.robotstxt.Grant;
import com.panforge.robotstxt.RobotsTxt;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/** 
 * Represents a web crawler for compiling candidate-specifc news article information.
 */
public class WebCrawler {
  // Mappings of (website robots.txt URL, the next allowed time to access, in milliseconds) for
  // respecting the required crawl delay.
  private Map<String, Long> nextAccessTimes = new HashMap<>();
  private final static String CUSTOM_SEARCH_KEY = "";
  private final static String CUSTOM_SEARCH_ENGINE_ID = "";
  private final static String TEST_URL =
      "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";

  /** 
   * Compiles news articles for the candidate with the specified {@code candidateName} and
   * {@code candidateId}:
   * 1. Obtains news article URLs from Google Custom Search.
   * 2. Checks for permission to web-scrape.
   * 3. Web-scrapes if permitted.
   * 4. Extracts content from HTML structure.
   * 5. Checks content relevancy to the candidate of interest.
   * 6. Processes content.
   * 7. Stores processed content in the database.
   */
  public void compileNewsArticle(String candidateName, String candidateId) {
    List<String> urls = getUrlsFromCustomSearch(candidateName);
    for (String url : urls) {
      Optional<NewsArticle> potentialNewsArticle = scrapeAndExtractHtml(url);
      if (!potentialNewsArticle.isPresent()) {
        continue;
      }
      NewsArticle newsArticle = potentialNewsArticle.get();
      RelevancyChecker relevancyChecker;
      try {
        relevancyChecker = new RelevancyChecker();
      } catch (IOException e) {
        continue;
      }
      if (!relevancyChecker.isRelevant(newsArticle, candidateName)) {
        continue;
      }
      NewsArticle processedNewsArticle = NewsContentProcessor.process(newsArticle);
      storeInDatabase(candidateId, processedNewsArticle);
    }
  }

  // @TODO [Might adopt in {@code compileNewsArticle}.]
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
  //   try {
  //     TimeUnit.MILLISECONDS.sleep(timeToDelay);
  //   } catch (InterruptedException e) {}
  // }

  // @TODO [Test with Google Custom Search. Extract other metadata.]
  /** 
   * Searches for {@code candidateName} in the Google Custom Search engine and finds URLs of news
   * articles.
   *
   * @see <a href=
   *    "https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/" +
   *    "http/examples/client/ClientWithResponseHandler.java">Code reference</a>
   */
  public List<String> getUrlsFromCustomSearch(String candidateName) {
    List<String> urls;
    String request =
        String.format("https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s",
            CUSTOM_SEARCH_KEY, CUSTOM_SEARCH_ENGINE_ID, candidateName.replace(" ", "%20"));
    CloseableHttpClient httpclient = HttpClients.createDefault();
    try {
      HttpGet httpGet = new HttpGet(request);
      ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
        @Override
        public String handleResponse(final HttpResponse response) throws IOException {
          int status = response.getStatusLine().getStatusCode();
          if (status >= 200 && status < 300) {
              HttpEntity entity = response.getEntity();
              return entity != null ? EntityUtils.toString(entity) : null;
          } else {
              httpclient.close();
              throw new ClientProtocolException("Unexpected response status: " + status);
          }
        }
      };
      String responseBody = httpclient.execute(httpGet, responseHandler);
      httpclient.close();
      Gson gson = new Gson();
      Object jsonResponse = gson.fromJson(responseBody, Object.class);
      //@TODO [Unpack {@code jsonResponse} and find URLs.]
      return Arrays.asList(TEST_URL);
    } catch (IOException e){
      System.out.println("[ERROR] Error occurred with fetching URLs from Custom Search: " + e);
      return Arrays.asList(TEST_URL);
    }
  }

  /** 
   * Checks robots.txt for permission to web-scrape, scrapes webpage if permitted and extracts
   * textual content. Returns an empty {@code Optional<NewsArticle>} in the event of an exception.
   */
  public Optional<NewsArticle> scrapeAndExtractHtml(String url) {
    try {
      URL formattedUrl = new URL(url);
      URL robotsUrl = new URL(formattedUrl.getProtocol(), formattedUrl.getHost(),
          "/robots.txt");
      InputStream robotsTxtStream = robotsUrl.openStream();
      RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
      String webpagePath = formattedUrl.getPath();
      Grant grant = robotsTxt.ask("*", webpagePath);
      // Check permission to access and respect the required crawl delay.
      if (grant == null || grant.hasAccess()) {
        if (grant != null && grant.getCrawlDelay() != null) {
          if (!waitForAndSetCrawlDelay(grant, robotsUrl.toString())) {
            return Optional.empty();
          }
        }
        InputStream webpageStream = new URL(url).openStream();
        return NewsContentExtractor.extractContentFromHtml(webpageStream, url);
      } else {
        return Optional.empty();
      }
    } catch (Exception e) {
      System.out.println("[ERROR] Error occured during web scraping: " + e);
      return Optional.empty();
    }
  }

  /** 
   * Waits for the required crawl delay to pass if necessary and makes a note of the required
   * crawl delay. Returns true if the aforementioned process succeeded. {@code grant} is expected
   * to non-null.
   */
  private boolean waitForAndSetCrawlDelay(Grant grant, String url) {
    if (nextAccessTimes.containsKey(url)) {
      if (!waitIfNecessary(url)) {
        return false;
      }
      nextAccessTimes.replace(url,
                              System.currentTimeMillis() + grant.getCrawlDelay() * 1000);
    } else {
      nextAccessTimes.put(url,
                          System.currentTimeMillis() + grant.getCrawlDelay() * 1000);
    }
    return true;
  }

  /** 
   * Waits for {@code timeToDelay} milliseconds if necessary and returns true if the pause
   * succeeded or if the pause was unnecessary.
   */
  private boolean waitIfNecessary(String url) {
    if (System.currentTimeMillis() < nextAccessTimes.get(url)) {
      try {
        TimeUnit.MILLISECONDS.sleep(nextAccessTimes.get(url) - System.currentTimeMillis());
      } catch (InterruptedException e) {
        return false;
      }
    }
    return true;
  }

  // @TODO [Fill in other properties: published date, publisher.]
  /** 
   * Stores {@code NewsArticle}'s metadata and content into the database, following a predesigned
   * database schema.
   * Requires "gcloud config set project project-ID" to be set correctly.
   * {@code content} and {@code abbreviatedContent} are excluded form database indexes, which are
   * additional data structures built to enable efficient lookup on non-keyed properties. Because
   * we will not query {@code NewsArticle} Datastore entities via {@code content} or
   * {@code abbreviatedContent}, we will not use indexes regardless.
   */
  public void storeInDatabase(String candidateId, NewsArticle newsArticle) {
    Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    Key newsArticleKey = datastore.newKeyFactory()
        .setKind("NewsArticle")
        .newKey((long) newsArticle.getUrl().hashCode());
    Entity newsArticleEntity = Entity.newBuilder(newsArticleKey)
        .set("candidateId", datastore.newKeyFactory().setKind("Candidate").newKey(candidateId))
        .set("title", newsArticle.getTitle())
        .set("url", newsArticle.getUrl())
        .set("content", excludeStringFromIndexes(newsArticle.getContent()))
        .set("abbreviatedContent", excludeStringFromIndexes(newsArticle.getAbbreviatedContent()))
        .build();
    datastore.put(newsArticleEntity);
  }

  /** 
   * Converts {@code String} to {@code StringValue} and excludes the data from indexes, to avoid
   * the 1500-byte size limit for indexed data.
   */
  private StringValue excludeStringFromIndexes(String content) {
    return StringValue.newBuilder(content).setExcludeFromIndexes(true).build();
  }

  // For testing purposes.
  public static void main(String[] args) {
    WebCrawler myWebCrawler = new WebCrawler();
    myWebCrawler.compileNewsArticle("Alexandria Ocasio-Cortez", "123");
    // Prevent {@code java.lang.IllegalThreadStateException}.
    System.exit(0);
  }
}
