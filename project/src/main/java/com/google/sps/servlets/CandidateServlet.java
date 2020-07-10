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

package com.google.sps.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.Gson;
import com.google.sps.data.Candidate;
import com.google.sps.data.NewsArticle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 
 * Queries the backend database and serves candidate-specific information for the candidate page.
 */
@WebServlet("/candidate")
public class CandidateServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Extract candidate ID.
    String candidateId = request.getParameter("candidateId");

    // @TODO [Get (1) official election/candidate information.]
    Candidate candidateData = getCandidateData(candidateId);
    // Get (2) news article information.
    List<NewsArticle> newsArticlesData = findNewsArticles(candidateId);
    // @TODO [Get (3) social media feed.]
    
    // Find candidate-specific information. Package and convert the data to JSON.
    CandidatePageDataPackage dataPackage = new CandidatePageDataPackage(candidateData, newsArticlesData);
    Gson gson = new Gson();
    String dataPackageJson = gson.toJson(dataPackage);

    // Send data in JSON format as the servlet response for the directory page.
    response.setContentType("application/json;");
    response.getWriter().println(dataPackageJson);
  }

  // @TODO [Function for getting (1) official election/candidate information from the database.]
  private Candidate getCandidateData(String candidateId) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query candidateQuery = new Query("Candidate")
        .setFilter(new FilterPredicate("__key__", FilterOperator.EQUAL,
                   KeyFactory.createKey("Candidate", Long.parseLong(candidateId))));
    PreparedQuery candidateQueryResult = datastore.prepare(candidateQuery);
    Entity candidateData = candidateQueryResult.asSingleEntity();
    return new Candidate(candidateId,
                      (String)candidateData.getProperty("name"),
                      (String)candidateData.getProperty("partyAffiliation"),
                      (Boolean)candidateData.getProperty("isIncumbent"),
                      (String)candidateData.getProperty("photoURL"),
                      (String)candidateData.getProperty("email"),
                      (String)candidateData.getProperty("phone number"),
                      (String)candidateData.getProperty("website"));
  }
  /**
   * Queries the database for news articles about the candidate represented by {@code candidateId}.
   */
  private List<NewsArticle> findNewsArticles(String candidateId) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query newsArticleQuery = new Query("NewsArticle")
        .setFilter(new FilterPredicate("candidateId", FilterOperator.EQUAL,
                   KeyFactory.createKey("Candidate", Long.parseLong(candidateId))));
    PreparedQuery newsArticleQueryResult = datastore.prepare(newsArticleQuery);
    List<Entity> newsArticles = newsArticleQueryResult.asList(FetchOptions.Builder.withDefaults());
    List<NewsArticle> newsArticlesData = new ArrayList<>(newsArticles.size());
    for (Entity newsArticle : newsArticles) {
      NewsArticle newsArticleData = new NewsArticle((String) newsArticle.getProperty("title"),
                                                    (String) newsArticle.getProperty("url"),
                                                    (String) newsArticle.getProperty("publisher"),
                                                    (Date) newsArticle.getProperty("publishedDate"),
                                                    (String) newsArticle.getProperty("content"));
      newsArticlesData.add(newsArticleData);
    }
    return newsArticlesData;
  }

  // @TODO [Function for getting (3) social media information from the database.]

  // Class to package together different types of data as a HTTP response.
  class CandidatePageDataPackage {
    private Candidate candidateData;
    private List<NewsArticle> newsArticlesData;

    CandidatePageDataPackage(Candidate candidateData,
        List<NewsArticle> newsArticlesData) {
      this.candidateData = candidateData;
      this.newsArticlesData = newsArticlesData;
    }
  }
}
