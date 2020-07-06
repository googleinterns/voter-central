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
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.Gson;
import com.google.sps.data.DirectoryCandidate;
import com.google.sps.data.Election;
import com.google.sps.data.Position;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 
 * Queries the backend database and serves location-based search results of (brief)
 * election/candidate information for the directory page.
 */
@WebServlet("/data")
public class DataServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String address = request.getParameter("address");

    // Resolve {@code address} to a list of election names.
    // @TODO [Need to call the Civic Information API]
    List<String> electionNames = Arrays.asList("New York's 14th Congressional District Election");

    // Find election/candidate information. Package and convert the data to JSON.
    List<Election> electionsData = extractElectionInformation(electionNames);
    DirectoryPageDataPackage dataPackage = new DirectoryPageDataPackage(electionsData);
    Gson gson = new Gson();
    String dataPackageJson = gson.toJson(dataPackage);

    // Send data in JSON format as the servlet response for the directory page.
    response.setContentType("application/json;");
    response.getWriter().println(dataPackageJson);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {}

  /**
   * Queries the database for (brief version) official election/position/candidate information and
   * formats the data as {@code Election} objects. Correlates one {@code Election} with one or more
   * {@code Position}.
   */
  private List<Election> extractElectionInformation(List<String> electionNames) {
    List<Election> electionsData = new ArrayList<>(electionNames.size());
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    for (String electionName : electionNames) {
      Query electionQuery = new Query("Election")
          .setFilter(new FilterPredicate("__key__", FilterOperator.EQUAL,
                    KeyFactory.createKey("Election", electionName)));
      PreparedQuery electionQueryResult = datastore.prepare(electionQuery);
      Entity election = electionQueryResult.asSingleEntity();
      List<Position> positions =
          extractPositionInformation((List<String>) election.getProperty("candidatePositions"),
                                     (List<String>) election.getProperty("candidateIds"),
                                     (List<Boolean>) election.getProperty("candidateIncumbency"));
      electionsData.add(new Election(election.getKey().getName(),
                                     (Date) election.getProperty("date"),
                                     positions));
    }
    return electionsData;
  }

  /**
   * Formats a list of positions and their associated candidates' information. Correlates
   * a {@code Position} object with one or more {@code DierctoryCandidate}. Candidates running
   * for the same position are assumed to be consecutive in {@code candidates}.
   */
  private List<Position> extractPositionInformation(List<String> candidatePositions,
      List<String> candidateIds, List<Boolean> candidateIncumbency) {
    Set<String> distinctPositions = new HashSet<>(candidatePositions);
    List<Position> positions = new ArrayList<>(distinctPositions.size());
    for (String positionName : distinctPositions) {
      int startIndex = candidatePositions.indexOf(positionName);
      int endIndex = candidatePositions.lastIndexOf(positionName);
      List<DirectoryCandidate> candidates = new ArrayList<>(endIndex - startIndex + 1);
      for (int i = startIndex; i <= endIndex; i++) {
        String candidateId = candidateIds.get(i);
        boolean isIncumbent = candidateIncumbency.get(i);
        candidates.add(extractCandidateInformation(candidateId, isIncumbent));
      }
      positions.add(new Position(positionName, candidates));
    }
    return positions;
  }

  /**
   * Queries the database for (brief version) candidate information based on ID and formats the
   * data as a {@code DirectoryCandidate} object.
   */
  private DirectoryCandidate extractCandidateInformation(String candidateId,
      boolean isIncumbent) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query candidateQuery = new Query("Candidate")
        .setFilter(new FilterPredicate("__key__", FilterOperator.EQUAL,
                   KeyFactory.createKey("Candidate", Long.parseLong(candidateId))));
    PreparedQuery candidateQueryResult = datastore.prepare(candidateQuery);
    Entity candidate = candidateQueryResult.asSingleEntity();
    return new DirectoryCandidate(candidateId, 
                                  (String) candidate.getProperty("name"),
                                  (String) candidate.getProperty("partyAffiliation"),
                                  isIncumbent);
  }

  // Class to package together different types of data as a HTTP response.
  class DirectoryPageDataPackage {
    private List<Election> electionsData;

    DirectoryPageDataPackage(List<Election> electionsData) {
      this.electionsData = electionsData;
    }
  }
}
