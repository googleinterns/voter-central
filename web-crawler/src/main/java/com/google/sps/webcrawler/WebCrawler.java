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
import com.google.gson.Gson;
import com.google.sps.data.NewsArticle;
import com.google.sps.webcrawler.ContentExtractor;
import com.google.sps.webcrawler.ContentProcessor;
import com.google.sps.webcrawler.RelevancyChecker;
import com.panforge.robotstxt.Grant;
import com.panforge.robotstxt.RobotsTxt;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
      NewsArticle newsArticle = scrapeAndExtractHtml(url);
      if (!RelevancyChecker.isRelevant(newsArticle, candidateName)) {
        continue;
      }
      NewsArticle processedNewsArticle = ContentProcessor.process(newsArticle);
      storeInDatabase(candidateId, processedNewsArticle);
      // waitForMaxCrawlDelay();
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
              throw new ClientProtocolException("Unexpected response status: " + status);
          }
        }
      };
      String responseBody = httpclient.execute(httpGet, responseHandler);
      httpclient.close();
      Gson gson = new Gson();
      Object jsonResponse = gson.fromJson(responseBody, Object.class);
      //@TODO [Unpack {@code jsonResponse} and find URLs.]
      return Arrays.asList(
          "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html");
    } catch (IOException e){
      System.out.println("[ERROR] Error occurred with fetching URLs from Custom Search: " + e);
      return Arrays.asList(
          "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html");
    }
  }

  /** 
   * Checks robots.txt for permission to web-scrape, scrapes webpage if permitted and extracts
   * textual content.
   */
  public NewsArticle scrapeAndExtractHtml(String url) {
    String robotsUrl = url.substring(0, url.indexOf("/", url.indexOf("//") + 2) + 1)
        + "robots.txt";
    try {
      InputStream robotsTxtStream = new URL(robotsUrl).openStream();
      RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
      String webpagePath = url.substring(url.indexOf("/", url.indexOf("//") + 2));
      Grant grant = robotsTxt.ask("*", webpagePath);
      // Check and set crawl delay.
      if (grant != null && grant.getCrawlDelay() != null) {
        if (nextAccessTimes.containsKey(robotsUrl)) {
          // Wait until the crawl delay fully passes.
          if (System.currentTimeMillis() < nextAccessTimes.get(robotsUrl)) {
            if (!waitForCrawlDelay(nextAccessTimes.get(robotsUrl) - System.currentTimeMillis())) {
              return new NewsArticle();
            };
          }
          nextAccessTimes.replace(robotsUrl,
                                  System.currentTimeMillis() + grant.getCrawlDelay() * 1000);
        } else {
          System.out.println(System.currentTimeMillis());
          nextAccessTimes.put(robotsUrl,
                              System.currentTimeMillis() + grant.getCrawlDelay() * 1000);
        }
      }
      // Check permission to access.
      if (grant == null || grant.hasAccess()) {
        InputStream webpageStream = new URL(url).openStream();
        return ContentExtractor.extractContentFromHtml(webpageStream, url);
      } else {
        return new NewsArticle();
      }
    } catch (IOException e) {
      System.out.println("[ERROR] Error occured during web scraping.");
      return new NewsArticle();
    }
  }

  /** 
   * Waits for {@code timeToDelay} milliseconds and returns true if the pause was successful.
   */
  private boolean waitForCrawlDelay(long timeToDelay) {
    try {
      TimeUnit.MILLISECONDS.sleep(timeToDelay);
    } catch (InterruptedException e) {
      return false;
    }
    return true;
  }

  // @TODO [Fill in other properties: published date, publisher.]
  /** 
   * Stores {@code NewsArticle}'s metadata and content into the database, following a predesigned
   * database schema.
   * Requires "gcloud config set project project-ID" to be set correctly. Assumes "content" to be
   * occupy fewer than 1500 bytes.
   */
  public void storeInDatabase(String candidateId, NewsArticle newsArticle) {
    Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    Key newsArticleKey = datastore.newKeyFactory()
        .setKind("NewsArticle")
        .newKey((new Random()).nextLong());
    Entity newsArticleEntity = Entity.newBuilder(newsArticleKey)
        .set("candidateId", datastore.newKeyFactory().setKind("Candidate").newKey(candidateId))
        .set("title", newsArticle.getTitle())
        .set("url", newsArticle.getUrl())
        .set("content", combineContentInHtml(newsArticle.getContent()))
        .build();
    datastore.put(newsArticleEntity);
  }

  /** 
   * Combines a collection of {@code String} into a single {@code String} for future usage in HTML.
   */
  private String combineContentInHtml(List<String> content) {
    return String.join("<br>", content);
  }

  // For testing purposes.
  public static void main(String[] args) {
    WebCrawler myWebCrawler = new WebCrawler();
    myWebCrawler.compileNewsArticle("Alexandria Ocasio-Cortez", "123");
  }
}
