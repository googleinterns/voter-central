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

import com.google.sps.data.NewsArticle;
import java.util.Arrays;

/** Static utilities for processing textual content, such as abbreviation. */
public class NewsContentProcessor {
  static final int MAX_WORD_COUNT = 100;

  // @TODO [Implement more advanced processing methods.]
  /** Extracts the first {@code MAX_WORD_COUNT} words from the news article content. */
  public static NewsArticle process(NewsArticle originalNewsArticle) {
    NewsArticle newsArticle = new NewsArticle(originalNewsArticle);
    String[] splitContent = newsArticle.getContent().split(" ");
    int wordCount = splitContent.length;
    int allowedLength = Math.min(wordCount, MAX_WORD_COUNT);
    String processedContent =
        String.join(" ", Arrays.asList(splitContent).subList(0, allowedLength));
    newsArticle.setAbbreviatedContent(processedContent);
    return newsArticle;
  }
}
