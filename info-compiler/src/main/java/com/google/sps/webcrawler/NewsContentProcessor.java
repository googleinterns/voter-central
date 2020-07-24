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

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.ReadChannel;
import com.google.sps.data.NewsArticle;
import com.google.sps.infocompiler.Config;
import java.io.FileInputStream; 
import java.io.InputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.library.linkanalysis.PageRank;
import org.apache.flink.types.DoubleValue;
import org.apache.flink.types.IntValue;
import org.apache.flink.types.StringValue;

/** Static utilities for processing textual content, such as abbreviations. */
public class NewsContentProcessor {
  static final int MAX_WORD_COUNT = 100;
  private static final int SUMMARIZATION_MAX_SENTENCE_NUMBER = 3;
  private static final int PAGERANK_MAX_ITER = 100;
  private static final double PAGERANK_DAMPEN_FACTOR = 0.1;
  private static final double PAGERANK_CONVERGENCE_THRESHOLD = 0.0001;
  private static final double SIMILARITY_THRESHOLD = 0.2;
  private static final boolean PAGERANK_INCLUDE_ZERO_DEGREES_VERTICES = true;
  private static final Storage storage =
      StorageOptions.newBuilder().setProjectId(Config.PROJECT_ID).build().getService();

  private static final Comparator<PageRank.Result> PAGERANK_SCORE_DESCENDING =
      new Comparator<PageRank.Result>() {
    @Override
    public int compare(PageRank.Result a, PageRank.Result b) {
      // Higher score comes first. With the same score, smaller index (sentences that appear
      // early) comes first.
      if (a.getPageRankScore().getValue() < b.getPageRankScore().getValue() ||
          (a.getPageRankScore().getValue() == b.getPageRankScore().getValue() && 
              ((IntValue) a.getVertexId0()).getValue()
                  > ((IntValue) b.getVertexId0()).getValue())) {
        return 1;
      } else {
        return -1;
      }
    }
  };

  private static final Comparator<PageRank.Result> SENTENCES_ORIGINAL_ORDER =
      new Comparator<PageRank.Result>() {
    @Override
    public int compare(PageRank.Result a, PageRank.Result b) {
      // Smaller index (sentences that appear early) comes first.
      if (((IntValue) a.getVertexId0()).getValue() > ((IntValue) b.getVertexId0()).getValue()) {
        return 1;
      } else {
        return -1;
      }
    }
  };

  /** Extracts the first {@code MAX_WORD_COUNT} words from the news article content. */
  public static void abbreviate(NewsArticle newsArticle) {
    String[] splitContent = newsArticle.getContent().split(" ");
    int wordCount = splitContent.length;
    int allowedLength = Math.min(wordCount, MAX_WORD_COUNT);
    String abbreviatedContent =
        String.join(" ", Arrays.asList(splitContent).subList(0, allowedLength));
    newsArticle.setAbbreviatedContent(abbreviatedContent);
  }

  /**
   * Extractively summarizes the news article content by computing inter-sentence similarity and
   * applying PageRank to find the most meaningful sentences. The chosen sentences are arranged
   * in the original order they appeared in. Sets empty summarized content in the event of an
   * exception, such as if {@code SentenceModel} or {@code TokenizerModel} model instantiation
   * fails, of if {@code PageRank} algorithm fails. {@code SENTENCE_DETECTOR_FILE} and {@code
   * TOKENIZER_FILE} must be prepared.
   *
   * @see <a href="https://towardsdatascience.com/understand-text-summarization-and-create-your-
   *     own-summarizer-in-python-b26a9f09fc70></a>
   */
  public static void summarize(NewsArticle newsArticle) {
    String rawContent = newsArticle.getContent();
    String[] sentences;
    try {
      sentences = breakIntoSentences(rawContent);
    } catch (IOException e) {
      newsArticle.setSummarizedContent("");
      return;
    }
    if (sentences.length <= SUMMARIZATION_MAX_SENTENCE_NUMBER) {
      newsArticle.setSummarizedContent(rawContent);
      return;
    }
    List<PageRank.Result<IntValue>> ranking;
    try {
      Graph<IntValue, StringValue, DoubleValue> similarityGraph = buildSimilarityGraph(sentences);
      ranking = getRanking(similarityGraph);
    } catch (Exception e) {
      newsArticle.setSummarizedContent("");
      return;
    }
    String summarizedContent = extractSentencesBasedOnRanking(ranking, sentences);
    newsArticle.setSummarizedContent(summarizedContent);
  }

  /** Breaks down {@code rawContent} into sentences. */
  private static String[] breakIntoSentences(String rawContent) throws IOException {
    InputStream modelFile = buildModelFileStream(Config.OPEN_NLP_SENTENCE_DETECTOR_FILE);
    SentenceModel model = new SentenceModel(modelFile);
    SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
    return sentenceDetector.sentDetect(rawContent);
  }

  /**
   * Builds the similarity graph among all sentences. The vertex (k, vv) represents the k-th
   * sentence. The edge (k1, k2, ev) represents a similarity of ev between the k1-th and k2-th
   * sentences. An edge exists only when the two sentences are similar "enough", as determined
   * by {@code SIMILARITY_THRESHOLD}.
   */
  private static Graph<IntValue, StringValue, DoubleValue> buildSimilarityGraph(String[] sentences)
      throws IOException {
    List<Vertex<IntValue, StringValue>> vertices =
        new ArrayList<Vertex<IntValue, StringValue>>(sentences.length);
    List<Edge<IntValue, DoubleValue>> edges =
        new ArrayList<Edge<IntValue, DoubleValue>>((int) Math.pow(sentences.length, 2));
    for (int i = 0; i < sentences.length; i++) {
      vertices.add(new Vertex(new IntValue(i), new StringValue(sentences[i])));
      for (int j = i + 1; j < sentences.length; j++) {
        String[] sentenceA = tokenizeSentence(sentences[i]);
        String[] sentenceB = tokenizeSentence(sentences[j]);
        double similarity = computeInterSentenceSimilarity(sentenceA, sentenceB);
        if (similarity >= SIMILARITY_THRESHOLD) {
          edges.add(new Edge(new IntValue(i), new IntValue(j), new DoubleValue(similarity)));
          edges.add(new Edge(new IntValue(j), new IntValue(i), new DoubleValue(similarity)));
        }
      }
    }
    return Graph.fromCollection(vertices, edges, ExecutionEnvironment.createLocalEnvironment());
  }

  /**
   * Finds sentence ranking by applying the PageRank algorithm to {@code similarityGraph} and
   * sorting all sentences based on descending PageRank scores (high-scored sentence comes first).
   * If two sentences have the same PageRank score, the sentence that comes early in the original
   * content comes first.
   */
  private static List<PageRank.Result<IntValue>> getRanking(
      Graph<IntValue, StringValue, DoubleValue> similarityGraph) throws Exception {
    PageRank pageRank =
        new PageRank(PAGERANK_DAMPEN_FACTOR, PAGERANK_MAX_ITER, PAGERANK_CONVERGENCE_THRESHOLD);
    List<PageRank.Result<IntValue>> pageRankResults =
        pageRank.setIncludeZeroDegreeVertices(PAGERANK_INCLUDE_ZERO_DEGREES_VERTICES)
            .run(similarityGraph).collect();
    Collections.sort(pageRankResults, PAGERANK_SCORE_DESCENDING);
    return pageRankResults;
  }

  /**
   * Extracts the most important {@code SUMMARIZATION_MAX_SENTENCE_NUMBER} sentences for expressing
   * the overall meaning of {@code sentences}, based on {@code ranking}, and re-arranges the
   * sentences based on their original order.
   */
  private static String extractSentencesBasedOnRanking(List<PageRank.Result<IntValue>> ranking,
      String[] sentences) {
    int sentenceNumber = Math.min(SUMMARIZATION_MAX_SENTENCE_NUMBER, sentences.length);
    List<PageRank.Result<IntValue>> subRanking = ranking.subList(0, sentenceNumber);
    Collections.sort(subRanking, SENTENCES_ORIGINAL_ORDER);
    String summarizedContent = "";
    for (int i = 0; i < sentenceNumber; i++) {
      int sentenceIndex = subRanking.get(i).getVertexId0().getValue();
      summarizedContent += sentences[sentenceIndex];
      if (i != sentenceNumber - 1) {
        summarizedContent += " ";
      }
    }
    return summarizedContent;
  }

  /** Tokenizes {@code sentence} into individual words; */
  private static String[] tokenizeSentence(String sentence) throws IOException {
    InputStream modelFile = buildModelFileStream(Config.OPEN_NLP_TOKENIZER_FILE);
    TokenizerModel model = new TokenizerModel(modelFile);
    TokenizerME tokenizer = new TokenizerME(model);
    return tokenizer.tokenize(sentence.toLowerCase());
  }

  /** Fetches a model file from Cloud Storage and builds a stream for the file. */
  private static InputStream buildModelFileStream(String filename) throws IOException {
    Blob modelFileBlob = storage.get(Config.OPEN_NLP_MODEL_FILES_BUCKET_NAME, filename,
                                     Storage.BlobGetOption.userProject(Config.PROJECT_ID));
    ReadChannel modelFileReader = modelFileBlob.reader();
    return Channels.newInputStream(modelFileReader);
  }

  /** Computes the cosine similarity between {@code sentenceA} and {@code sentenceB}. */
  private static double computeInterSentenceSimilarity(String[] sentenceA, String[] sentenceB) {
    Map<String, Integer> vocab = buildVocabFromSentences(sentenceA, sentenceB);
    RealVector representationA = buildVectorRepresentation(vocab, sentenceA);
    RealVector representationB = buildVectorRepresentation(vocab, sentenceB);
    return computeCosineSimilarity(representationA, representationB);
  }

  /** Builds the vocabulary of {@code sentenceA} and {@code sentenceB}. */
  private static Map<String, Integer> buildVocabFromSentences(String[] sentenceA,
      String[] sentenceB) {
    Set<String> vocabSet = new HashSet<>(Arrays.asList(sentenceA));
    vocabSet.addAll(Arrays.asList(sentenceB));
    Map<String, Integer> vocab = new HashMap<>(vocabSet.size());
    int index = 0;
    for (String word : vocabSet) {
      vocab.put(word, index);
      index++;
    }
    return vocab;
  }

  /**
   * Construct vector representations of {@code sentenceA} and {@code sentenceB} with respect to
   * {@code vocab}.
   */
  private static RealVector buildVectorRepresentation(Map<String, Integer> vocab,
      String[] sentence) {
    RealVector representation = new ArrayRealVector(vocab.size()); // Zero vector.
    for (String word : sentence) {
      int index = vocab.get(word);
      representation.setEntry(index, representation.getEntry(index) + 1);
    }
    return representation;
  }

  /** Computes the cosine similarity between {@code vectorA} and {@code vectorB}. */
  private static double computeCosineSimilarity(RealVector vectorA, RealVector vectorB) {
    return vectorA.dotProduct(vectorB) / (vectorA.getNorm() * vectorB.getNorm());
  }
}
