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
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import java.io.InputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

/** 
 * Static utilities for extracting textual content from HTML pages.
 */
public class NewsContentExtractor {
  private static final HtmlParser parser = new HtmlParser();

  /**
   * Extracts textual content from HTML. Packages data into {@code NewsArticle}. Returns an empty
   * {@code Optional<NewsArticle>} in the event of an exception, which may be caused by errors
   * such as failure in reading in the HTML source code from {@code htmlFileStream}.
   */
  public static Optional<NewsArticle> extractContentFromHtml(InputStream htmlFileStream,
      String url) {
    BoilerpipeContentHandler boilerpipeHandler =
        new BoilerpipeContentHandler(new BodyContentHandler(), new ArticleExtractor());
    Metadata metadata = new Metadata();
    try {
      parser.parse(htmlFileStream, boilerpipeHandler, metadata);
      TextDocument textDocument = boilerpipeHandler.getTextDocument();
      return Optional.of(new NewsArticle(textDocument.getTitle(),
                                         url,
                                         textDocument.getContent()));
    } catch (IOException | SAXException | TikaException | NullPointerException e) {
      return Optional.empty();
    }
  }
}
