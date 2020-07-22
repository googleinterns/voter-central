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

package com.google.sps.data;

import java.util.Date;

/** A news article, including its metadata and content. */
public class NewsArticle {
  private static final int LOWEST_PRIORITY = 10;
  private String title;
  private String url;
  private String content;
  private String abbreviatedContent;
  private String publisher;
  private Date publishedDate;
  private int priority = LOWEST_PRIORITY;

  /**
   * Consturcts a {@code NewsArticle}. If {@code publishedDate) is null, initialize with the
   * standard base time: January 1, 1970, 00:00:00 GMT.
   */
  public NewsArticle(String url, String publisher, Date publishedDate) {
    this.url = (url == null) ? "" : url;
    this.publisher = (publisher == null) ? "" : publisher;
    this.publishedDate = (publishedDate == null) ? new Date(0) : publishedDate;
  }

  public NewsArticle(NewsArticle newsArticle) {
    this.title = newsArticle.title;
    this.url = newsArticle.url;
    this.content = newsArticle.content;
    this.abbreviatedContent = newsArticle.abbreviatedContent;
    this.publisher = newsArticle.publisher;
    this.publishedDate = (Date) newsArticle.publishedDate.clone(); // Date is mutable.
    this.priority = newsArticle.priority;
  }

  public String getTitle() {
    return this.title;
  }

  public String getUrl() {
    return this.url;
  }

  public String getContent() {
    return this.content;
  }

  public String getAbbreviatedContent() {
    return this.abbreviatedContent;
  }

  public String getPublisher() {
    return this.publisher;
  }

  public Date getPublishedDate() {
    return this.publishedDate;
  }

  public int getPriority() {
    return this.priority;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setAbbreviatedContent(String abbreviatedContent) {
    this.abbreviatedContent = abbreviatedContent;
  }

  /**
   * Compares whether individual instance variables are equal, with the exception that content
   * can be the substring of another without being exactly the same. The exception is allowed
   * because, when scraping real webpages, the scraped content also includes the actual news
   * articles body as well as changing content such as ads, descriptions of other articles.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof NewsArticle)) {
      return false;
    }
    NewsArticle other = (NewsArticle) obj;
    return ((this.title == null && other.title == null)
            || (this.title != null && this.title.equals(other.title)))
        && this.url.equals(other.url)
        && ((this.content == null && other.content == null)
            || (this.content != null && other.content != null
                && (this.content.contains(other.content) || other.content.contains(this.content))))
        && ((this.abbreviatedContent == null && other.abbreviatedContent == null)
            || (this.abbreviatedContent != null
                && this.abbreviatedContent.equals(other.abbreviatedContent)))
        && this.publisher.equals(other.publisher)
        && this.publishedDate.equals(other.publishedDate)
        && this.priority == other.priority;
  }
}
