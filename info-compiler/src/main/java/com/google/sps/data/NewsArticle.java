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

/** A news article, including its metadata and content. */
public class NewsArticle {
  private String title;
  private String url;
  private String content;
  private String abbreviatedContent;

  public NewsArticle(String title, String url, String content) {
    this.title = (title == null) ? "" : title;
    this.url = (url == null) ? "" : url;
    this.content = (content == null) ? "" : content;
    this.abbreviatedContent = null;
  }

  public NewsArticle(NewsArticle newsArticle) {
    this.title = newsArticle.getTitle();
    this.url = newsArticle.getUrl();
    this.content = newsArticle.getContent();
    this.abbreviatedContent = newsArticle.getAbbreviatedContent();
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
    return this.title.equals(other.getTitle())
        && this.url.equals(other.getUrl())
        && (this.content.contains(other.getContent()) || other.getContent().contains(this.content))
        && ((this.abbreviatedContent == null && other.getAbbreviatedContent() == null)
            || (this.abbreviatedContent != null && other.getAbbreviatedContent() != null
                && this.abbreviatedContent.equals(other.getAbbreviatedContent())));
  }
}
