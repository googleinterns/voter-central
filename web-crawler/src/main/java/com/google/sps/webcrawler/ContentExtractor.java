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
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

/** 
 * Provides a tool for extracting textual content from HTML.
 */
public class ContentExtractor {

  /** 
   * Extracts textual content from HTML. Packages data into {@code NewsArticle}.
   */
  public static NewsArticle extractContentFromHtml(InputStream htmlFileStream, String url) {
    BodyContentHandler bodyHandler = new BodyContentHandler();
    ArticleExtractor articleExtractor = new ArticleExtractor();
    BoilerpipeContentHandler boilerpipeHandler =
        new BoilerpipeContentHandler(bodyHandler, articleExtractor);
    HtmlParser parser = new HtmlParser();
    Metadata metadata = new Metadata();
    try {
      parser.parse(htmlFileStream, boilerpipeHandler, metadata);
      TextDocument textDocument = boilerpipeHandler.getTextDocument();
      return formatNewsArticleFromDocument(textDocument, url);
    } catch (IOException | SAXException | TikaException e) {
      return new NewsArticle();
    }
  }

  /** 
   * Formats and packages information of a {@code NewsArticle} from the parsed result of a
   * {@code TextDocument}.
   */
  private static NewsArticle formatNewsArticleFromDocument(TextDocument textDocument, String url) {
    List<String> content = new LinkedList<>();
    for (TextBlock textBlock : textDocument.getTextBlocks()) {
      if (textBlock.isContent()) {
        content.add(textBlock.getText());
      }
    }
    return new NewsArticle(textDocument.getTitle(), url, content);
  }
}
