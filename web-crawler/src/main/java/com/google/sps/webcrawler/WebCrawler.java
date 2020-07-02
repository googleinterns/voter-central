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

import com.google.sps.data.NewsArticle;
import com.google.sps.webcrawler.ContentExtractor;
import com.google.sps.webcrawler.ContentProcessor;
import com.google.sps.webcrawler.RelevancyChecker;
import com.panforge.robotstxt.Grant;
import com.panforge.robotstxt.RobotsTxt;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/** 
 * Represents a web crawler for compiling candidate-specifc news article information.
 */
public class WebCrawler {
  private int maxCrawlDelay = 0;

  // @TODO
  // public void compileNewsArticle(String candidateName) {
  //   List<String> urls = getUrlsFromCustomSearch(candidateName);
  //   for (String url : urls) {
  //     NewsArticle newsArticle = scrapeAndExtractHtml(url);
  //     if (!RelevancyChecker.isRelevant(newsArticle, candidateName)) {
  //       continue;
  //     }
  //     NewsArticle processedNewsArticle = ContentProcessor.process(newsArticle);
  //     storeContentInDatabase(processedNewsArticle);
  //   }
  // }

  // @TODO public List<String> getUrlsFromCustomSearch(candidateName) {}

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
      if (grant == null || grant.hasAccess()) {
        InputStream webpageStream = new URL(url).openStream();
        return ContentExtractor.extractContentFromHtml(webpageStream);
      } else {
        return new NewsArticle();
      }
      // @TODO [Handle crawl delay]
      // if (grant != null && grant.getCrawlDelay() != null) {
      //   maxCrawlDelay = Math.max(maxCrawlDelay, grant.getCrawlDelay());
      // }
    } catch (IOException e) {
      System.out.println("[ERROR] Error occured during web scraping.");
      return new NewsArticle();
    }
  }

  // @TODO public void storeContentInDatabase(NewsArticle newsArticle) {}

  // For testing purposes.
  public static void main(String[] args) {
    WebCrawler myWebCrawler = new WebCrawler();
    NewsArticle myNewsArticle = myWebCrawler.scrapeAndExtractHtml(
        "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html");
    System.out.println(myNewsArticle.getTitle());
    System.out.println(myNewsArticle.getContent());
  }
}
