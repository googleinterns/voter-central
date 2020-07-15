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
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.html.HtmlParser;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xml.sax.SAXException;
import static org.mockito.Mockito.*;

/**
 * A tester for news article content extraction.
 */
@RunWith(JUnit4.class)
public final class NewsContentExtractorTest {
  private final static String URL =
    "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";
  private final static String WRONG_URL =
    "https://www.wrong.com";
  private final static String EMPTY = "";
  private final static NewsArticle NEWS_ARTICLE_WITH_EMPTY_CONTENT =
      new NewsArticle(EMPTY, URL, EMPTY);
  private InputStream realWebpageStream;
  private NewsContentExtractor realNewsContentExtractor = new NewsContentExtractor();

  @Before
  public void createInputStream() throws IOException {
    realWebpageStream = new URL(URL).openStream();
  }

  @Test
  public void extractContentFromHtml_emptyDocParser() throws IOException, SAXException,
      TikaException {
    // Extract content and meta data using a parser that modifies the content handler to
    // receive an end-of-document notification via {@code endDocument()}. Therefore, an empty
    // article will be extracted, while the URL is set correctly to the passed in parameter.
    // Content processing hasn't occurred so the abbreivated content is null.
    HtmlParser parser = mock(HtmlParser.class);
    doAnswer(parseInvocation -> {
               BoilerpipeContentHandler boilerpipeHandler =
                   (BoilerpipeContentHandler) parseInvocation.getArguments()[1];
               boilerpipeHandler.endDocument();
               return null;
            }).when(parser).parse(anyObject(), anyObject(), anyObject());
    NewsContentExtractor newsContentExtractor = new NewsContentExtractor(parser);
    Optional<NewsArticle> potentialNewsArticle =
        newsContentExtractor.extractContentFromHtml(realWebpageStream,
                                                    NEWS_ARTICLE_WITH_EMPTY_CONTENT.getUrl());
    Assert.assertTrue(potentialNewsArticle.isPresent());
    NewsArticle newsArticle = potentialNewsArticle.get();
    Assert.assertEquals(newsArticle, NEWS_ARTICLE_WITH_EMPTY_CONTENT);
  }

  @Test
  public void extractContentFromHtml_IOExceptionStream() throws IOException {
    // The webpage stream throws {@code IOException} when being read. This exception should be
    // caught and no content will be extracted.
    InputStream badStream = mock(InputStream.class);
    when(badStream.read(anyObject(), anyInt(), anyInt())).thenThrow(new IOException());
    Optional<NewsArticle> potentialNewsArticle =
        realNewsContentExtractor.extractContentFromHtml(badStream, URL);
    Assert.assertFalse(potentialNewsArticle.isPresent());
  }

  // SAXException is a checked exception and thus invalid for mocking with {@code thenThrow()}.
  //
  // @Test
  // public void extractContentFromHtml_SAXExceptionStream() throws IOException {
  //   // The webpage stream throws {@code SAXException} when being read. This exception should be
  //   // caught and no content will be extracted.
  //   InputStream badStream = mock(InputStream.class);
  //   when(badStream.read(anyObject(), anyInt(), anyInt())).thenThrow(new SAXException());
  //   Optional<NewsArticle> potentialNewsArticle =
  //       realNewsContentExtractor.extractContentFromHtml(badStream, URL);
  //   Assert.assertFalse(potentialNewsArticle.isPresent());
  // }

  // TikaException is a checked exception and thus invalid for mocking with {@code thenThrow()}.
  //
  // @Test
  // public void extractContentFromHtml_TikaExceptionStream() throws IOException {
  //   // The webpage stream throws {@code TikaException} when being read. This exception should be
  //   // caught and no content will be extracted.
  //   InputStream badStream = mock(InputStream.class);
  //   when(badStream.read(anyObject(), anyInt(), anyInt())).thenThrow(new TikaException(""));
  //   Optional<NewsArticle> potentialNewsArticle =
  //       realNewsContentExtractor.extractContentFromHtml(badStream, URL);
  //   Assert.assertFalse(potentialNewsArticle.isPresent());
  // }

  @Test
  public void extractContentFromHtml_NullWebpageStream() {
    // The webpage stream is null, and it will cause a {@code NullPointerException}. No content
    // can be extracted.
    Optional<NewsArticle> potentialNewsArticle =
        realNewsContentExtractor.extractContentFromHtml(null, URL);
    Assert.assertFalse(potentialNewsArticle.isPresent());
  }

  @After
  public void closeInputStream() throws IOException {
    realWebpageStream.close();
  }
}
