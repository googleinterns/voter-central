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
import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.document.TextDocument;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.html.HtmlParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xml.sax.SAXException;

/**
 * A tester for news article content extraction.
 */
@RunWith(JUnit4.class)
public final class NewsContentExtractorTest {
  private static final String URL =
    "https://www.cnn.com/2020/06/23/politics/aoc-ny-primary-14th-district/index.html";
  private static final String EMPTY = "";
  private static final String TITLE = "News Article Title";
  private static final String CONTENT = "News article content.";
  private static final TextBlock TEXT_BLOCK = new TextBlock(CONTENT);
  private static final TextDocument TEXT_DOC_REGULAR =
      new TextDocument(TITLE, Arrays.asList(TEXT_BLOCK));
  private static final TextDocument TEXT_DOC_WITHOUT_TITLE =
      new TextDocument(Arrays.asList(TEXT_BLOCK));

  private InputStream webpageStream;
  private HtmlParser parser;
  private NewsContentExtractor realNewsContentExtractor = new NewsContentExtractor();
  private NewsContentExtractor newsContentExtractor;
  private BoilerpipeContentHandler boilerpipeHandler;

  @Before
  public void createInputStream() {
    webpageStream = mock(InputStream.class);
    parser = mock(HtmlParser.class);
    newsContentExtractor = new NewsContentExtractor(parser);
    boilerpipeHandler = mock(BoilerpipeContentHandler.class);
  }

  @Test
  public void extractContentFromHtml_regularTextDoc() throws IOException, SAXException,
      TikaException {
    // Extract content from a {@code BoilerpipeContentHandler} that returns {@code
    // TEXT_DOC_REGULAR}. Content processing hasn't occurred so the abbreivated content is null.
    when(boilerpipeHandler.getTextDocument()).thenReturn(TEXT_DOC_REGULAR);
    Metadata metadata = new Metadata();
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    NewsArticle expectedNewsArticle = new NewsArticle(newsArticle);
    newsContentExtractor.extractContentFromHtml(boilerpipeHandler, metadata, webpageStream,
                                                newsArticle);
    expectedNewsArticle.setTitle(TITLE);
    expectedNewsArticle.setContent(CONTENT);
    assertThat(newsArticle).isEqualTo(expectedNewsArticle);
  }

  @Test
  public void extractContentFromHtml_textDocWithoutTitle() throws IOException, SAXException,
      TikaException {
    // Extract content from a {@code BoilerpipeContentHandler} that returns {@code
    // TEXT_DOC_WITHOUT_TITLE}. Content processing hasn't occurred so the abbreivated content is
    // null.
    when(boilerpipeHandler.getTextDocument()).thenReturn(TEXT_DOC_WITHOUT_TITLE);
    Metadata metadata = new Metadata();
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    NewsArticle expectedNewsArticle = new NewsArticle(newsArticle);
    newsContentExtractor.extractContentFromHtml(boilerpipeHandler, metadata, webpageStream,
                                                newsArticle);
    expectedNewsArticle.setTitle(EMPTY);
    expectedNewsArticle.setContent(CONTENT);
    assertThat(newsArticle).isEqualTo(expectedNewsArticle);
  }

  @Test
  public void extractContentFromHtml_emptyDocParser() throws IOException, SAXException,
      TikaException {
    // Extract content and meta data using a parser that modifies the content handler to
    // receive an end-of-document notification via {@code endDocument()}. Therefore, an empty
    // article will be extracted. Content processing hasn't occurred so the abbreivated content is
    // null.
    doAnswer(parseInvocation -> {
               BoilerpipeContentHandler boilerpipeHandler =
                   (BoilerpipeContentHandler) parseInvocation.getArguments()[1];
               boilerpipeHandler.endDocument();
               return null;
            }).when(parser).parse(anyObject(), anyObject(), anyObject());
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    NewsArticle expectedNewsArticle = new NewsArticle(newsArticle);
    newsContentExtractor.extractContentFromHtml(webpageStream, newsArticle);
    expectedNewsArticle.setTitle(EMPTY);
    expectedNewsArticle.setContent(EMPTY);
    assertThat(newsArticle).isEqualTo(expectedNewsArticle);
  }

  @Test
  public void extractContentFromHtml_IOExceptionStream() throws IOException {
    // The webpage stream throws {@code IOException} when being read. This exception should be
    // caught and an empty content should be set.
    when(webpageStream.read(anyObject(), anyInt(), anyInt())).thenThrow(new IOException());
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    realNewsContentExtractor.extractContentFromHtml(webpageStream, newsArticle);
    assertThat(newsArticle.getContent()).isEqualTo(EMPTY);
  }

  @Test
  public void extractContentFromHtml_NullWebpageStream() {
    // The webpage stream is null, and it will cause a {@code NullPointerException}. An empty
    // content should be set.
    NewsArticle newsArticle = new NewsArticle(URL, null, null);
    realNewsContentExtractor.extractContentFromHtml(null, newsArticle);
    assertThat(newsArticle.getContent()).isEqualTo(EMPTY);
  }
}
