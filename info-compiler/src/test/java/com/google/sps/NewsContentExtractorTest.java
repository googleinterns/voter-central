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
  private final static String TITLE = "News Article Title";
  private final static String CONTENT = "News article content.";
  private final static TextBlock TEXT_BLOCK = new TextBlock(CONTENT);
  private final static TextDocument TEXT_DOC_REGULAR =
      new TextDocument(TITLE, Arrays.asList(TEXT_BLOCK));
  private final static TextDocument TEXT_DOC_WITHOUT_TITLE =
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
    Optional<NewsArticle> potentialNewsArticle =
        newsContentExtractor.extractContentFromHtml(boilerpipeHandler, metadata, webpageStream,
                                                    URL);
    Assert.assertTrue(potentialNewsArticle.isPresent());
    Assert.assertEquals(new NewsArticle(TITLE, URL, CONTENT), potentialNewsArticle.get());
  }

  @Test
  public void extractContentFromHtml_regularTextDocWrongUrlParam() throws IOException,
      SAXException, TikaException {
    // Extract content from a {@code BoilerpipeContentHandler} that returns {@code
    // TEXT_DOC_REGULAR} while passing in {@code WRONG_URL}. Content processing hasn't occurred so
    // the abbreivated content is null.
    when(boilerpipeHandler.getTextDocument()).thenReturn(TEXT_DOC_REGULAR);
    Metadata metadata = new Metadata();
    Optional<NewsArticle> potentialNewsArticle =
        newsContentExtractor.extractContentFromHtml(boilerpipeHandler, metadata, webpageStream,
                                                    WRONG_URL);
    Assert.assertTrue(potentialNewsArticle.isPresent());
    Assert.assertEquals(new NewsArticle(TITLE, WRONG_URL, CONTENT), potentialNewsArticle.get());
  }

  @Test
  public void extractContentFromHtml_textDocWithoutTitle() throws IOException, SAXException,
      TikaException {
    // Extract content from a {@code BoilerpipeContentHandler} that returns {@code
    // TEXT_DOC_WITHOUT_TITLE}. Content processing hasn't occurred so the abbreivated content is
    // null.
    when(boilerpipeHandler.getTextDocument()).thenReturn(TEXT_DOC_WITHOUT_TITLE);
    Metadata metadata = new Metadata();
    Optional<NewsArticle> potentialNewsArticle =
        newsContentExtractor.extractContentFromHtml(boilerpipeHandler, metadata, webpageStream,
                                                    URL);
    Assert.assertTrue(potentialNewsArticle.isPresent());
    Assert.assertEquals(new NewsArticle(EMPTY, URL, CONTENT), potentialNewsArticle.get());
  }

  @Test
  public void extractContentFromHtml_emptyDocParser() throws IOException, SAXException,
      TikaException {
    // Extract content and meta data using a parser that modifies the content handler to
    // receive an end-of-document notification via {@code endDocument()}. Therefore, an empty
    // article will be extracted, while the URL is set correctly to the passed in parameter.
    // Content processing hasn't occurred so the abbreivated content is null.
    doAnswer(parseInvocation -> {
               BoilerpipeContentHandler boilerpipeHandler =
                   (BoilerpipeContentHandler) parseInvocation.getArguments()[1];
               boilerpipeHandler.endDocument();
               return null;
            }).when(parser).parse(anyObject(), anyObject(), anyObject());
    Optional<NewsArticle> potentialNewsArticle =
        newsContentExtractor.extractContentFromHtml(webpageStream, URL);
    Assert.assertTrue(potentialNewsArticle.isPresent());
    Assert.assertEquals(new NewsArticle(EMPTY, URL, EMPTY), potentialNewsArticle.get());
  }

  @Test
  public void extractContentFromHtml_IOExceptionStream() throws IOException {
    // The webpage stream throws {@code IOException} when being read. This exception should be
    // caught and no content will be extracted.
    when(webpageStream.read(anyObject(), anyInt(), anyInt())).thenThrow(new IOException());
    Optional<NewsArticle> potentialNewsArticle =
        realNewsContentExtractor.extractContentFromHtml(webpageStream, URL);
    Assert.assertFalse(potentialNewsArticle.isPresent());
  }

  @Test
  public void extractContentFromHtml_NullWebpageStream() {
    // The webpage stream is null, and it will cause a {@code NullPointerException}. No content
    // can be extracted.
    Optional<NewsArticle> potentialNewsArticle =
        realNewsContentExtractor.extractContentFromHtml(null, URL);
    Assert.assertFalse(potentialNewsArticle.isPresent());
  }
}
