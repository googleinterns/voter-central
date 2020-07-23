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

import com.google.sps.data.NewsArticle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xml.sax.SAXException;

/**
 * A tester for news article content processing.
 */
@RunWith(JUnit4.class)
public final class NewsContentProcessorTest {
  private static final String URL = "https://www.cnn.com/index.html";
  private static final String WORD = "word";
  private static final String EMPTY_CONTENT = "";
  private static final String REGULAR_CONTENT =
      "Washington (CNN)Freshman Democratic Rep. Alexandria Ocasio-Cortez will defeat former " +
      "longtime CNBC correspondent and anchor Michelle Caruso-Cabrera in a Democratic " +
      "primary election on Tuesday for New York's 14th Congressional District, CNN projects. " +
      "The district, which includes parts of the Bronx and Queens, is heavily Democratic and " +
      "considered to be a reliably blue seat. It was formerly held for years by veteran " +
      "Democrat Joe Crowley, who Ocasio-Cortez defeated in a major upset in the 2018 midterm " +
      "elections. Ocasio-Cortez, who has in her first term in office become a national " +
      "progressive icon, had significant advantages in the race, including strong national " +
      "name recognition, devoted supporters and significant fundraising. She had raised more " +
      "than $10.5 million, an amount that vastly exceeds the fundraising totals of any of her " +
      "challengers. In comparison, Caruso-Cabrera had raised more than $2 million, according " +
      "to the latest data from the Federal Election Commission.";
  private static final String SUMMARIZED_CONTENT =
      "The district, which includes parts of the Bronx and Queens, is heavily Democratic and " +
      "considered to be a reliably blue seat. It was formerly held for years by veteran Democrat" +
      " Joe Crowley, who Ocasio-Cortez defeated in a major upset in the 2018 midterm elections. " +
      "Ocasio-Cortez, who has in her first term in office become a national progressive icon, " +
      "had significant advantages in the race, including strong national name recognition, " +
      "devoted supporters and significant fundraising."; // The 2nd, 3rd, 4th sentences.

  private String LONG_CONTENT;
  private String MAX_CONTENT;
  private String SHORT_CONTENT;

  @Before
  public void createContent() {
    // Construct content strings.
    LONG_CONTENT = "";
    for (int i = 0; i < NewsContentProcessor.MAX_WORD_COUNT + 1; i++) {
      LONG_CONTENT += WORD + " ";
    }
    MAX_CONTENT = "";
    for (int i = 0; i < NewsContentProcessor.MAX_WORD_COUNT; i++) {
      if (i != NewsContentProcessor.MAX_WORD_COUNT - 1) {
        MAX_CONTENT += WORD + " ";
      } else {
        MAX_CONTENT += WORD; // Avoid the final space.
      }
    }
    SHORT_CONTENT = WORD;
  }

  @Test
  public void abbreviate_longContent() {
    // Process {@code LONG_CONTENT} and extract abbreviated content as {@code MAX_CONTENT}, which
    // contains the {@code MAX_WORD_COUNT} of words. After content processing, the title, URL and
    // content and other data remain the same.
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    newsArticle.setContent(LONG_CONTENT);
    NewsArticle expectedArticle = new NewsArticle(newsArticle);
    NewsContentProcessor.abbreviate(newsArticle);
    expectedArticle.setAbbreviatedContent(MAX_CONTENT);
    assertThat(newsArticle).isEqualTo(expectedArticle);
  }

  @Test
  public void abbreviate_shortContent() {
    // Process {@code SHORT_CONTENT} and extract abbreviated content as {@code SHORT_CONTENT},
    // which contains fewer than {@code MAX_WORD_COUNT} of words. After content processing, the
    // title, URL and content and other data remain the same.
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    newsArticle.setContent(SHORT_CONTENT);
    NewsArticle expectedArticle = new NewsArticle(newsArticle);
    NewsContentProcessor.abbreviate(newsArticle);
    expectedArticle.setAbbreviatedContent(SHORT_CONTENT);
    assertThat(newsArticle).isEqualTo(expectedArticle);
  }

  @Test
  public void abbreviate_emptyContent() {
    // Process {@code EMPTY_CONTENT} and extract abbreviated content as {@code EMPTY_CONTENT}.
    // After content processing, the title, URL, content and other data remain the same.
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    newsArticle.setContent(EMPTY_CONTENT);
    NewsArticle expectedArticle = new NewsArticle(newsArticle);
    NewsContentProcessor.abbreviate(newsArticle);
    expectedArticle.setAbbreviatedContent(EMPTY_CONTENT);
    assertThat(newsArticle).isEqualTo(expectedArticle);
  }

  @Test
  public void summarize_regularContent() throws Exception {
    // Summarize {@code REGULAR_CONTENT} and extract content as {@code SUMMARIZED_CONTENT}.
    // After content processing, the title, URL, content and other data remain the same.
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    newsArticle.setContent(REGULAR_CONTENT);
    NewsArticle expectedArticle = new NewsArticle(newsArticle);
    NewsContentProcessor.summarize(newsArticle);
    expectedArticle.setSummarizedContent(SUMMARIZED_CONTENT);
    System.out.println(newsArticle.getSummarizedContent());
    System.out.println(expectedArticle.getSummarizedContent());
    assertThat(newsArticle).isEqualTo(expectedArticle);
  }

  @Test
  public void summarize_emptyContent() throws Exception {
    // Summarize {@code EMPTY_CONTENT} and extract content as {@code EMPTY_CONTENT}.
    // After content processing, the title, URL, content and other data remain the same.
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    newsArticle.setContent(EMPTY_CONTENT);
    NewsArticle expectedArticle = new NewsArticle(newsArticle);
    NewsContentProcessor.summarize(newsArticle);
    expectedArticle.setSummarizedContent(EMPTY_CONTENT);
    assertThat(newsArticle).isEqualTo(expectedArticle);
  }
}
