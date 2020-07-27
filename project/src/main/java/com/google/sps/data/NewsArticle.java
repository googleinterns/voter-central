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

/** 
 * Represents a news article, including its metadata and content.
 */
public class NewsArticle {
  private String title;
  private String url;
  private String publisher;
  private Date publishedDate;
  private String content;
  // The first 100 words of {@code content}.
  private String abbreviatedContent;
  // The 5 most important sentences extracted from {@code content}.
  private String summarizedContent;
  private int priority;
  private NewsArticleCategory category;

  public NewsArticle(String title, String url, String publisher, Date publishedDate,
      String content, String abbreviatedContent, String summarizedContent, int priority) {
    this.title = title;
    this.url = url;
    this.publisher = publisher;
    this.publishedDate = publishedDate;
    this.content = content;
    this.abbreviatedContent = abbreviatedContent;
    this.summarizedContent = summarizedContent;
    this.priority = priority;
    this.category = null;
  }
}

class NewsArticleCategory {}
