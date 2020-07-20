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
import java.lang.Boolean;
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
    boolean listAllElections = Boolean.parseBoolean(request.getParameter("listAllElections"));

    // Find election/candidate information. Package and convert the data to JSON.
    List<Election> elections = extractElectionInformation(address, listAllElections);
    DirectoryPageDataPackage dataPackage = new DirectoryPageDataPackage(elections);
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
  private List<Election> extractElectionInformation(String address, boolean listAllElections) {
    List<Election> elections = new ArrayList<>();
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query electionQuery = new Query("Election");
    PreparedQuery electionQueryResult = datastore.prepare(electionQuery);
    List<Entity> electionsData = electionQueryResult.asList(FetchOptions.Builder.withDefaults());
    for (Entity election : electionsData) {
      if (!isRelevantElection(election, address, listAllElections)) {
        continue;
      }
      List<String> candidatePositionsData = 
          (List<String>) election.getProperty("candidatePositions");
      List<String> candidateIdsData = 
          (List<String>) election.getProperty("candidateIds");
      List<Boolean> candidateIncumbencyData = 
          (List<Boolean>) election.getProperty("candidateIncumbency");
      if (candidatePositionsData == null || candidateIdsData == null 
          || candidateIncumbencyData == null) {
        continue;
      }
      List<Position> positions =
          extractPositionInformation(candidatePositionsData, candidateIdsData,
                                    candidateIncumbencyData);
      elections.add(new Election(election.getKey().getName(),
                                (Date) election.getProperty("date"), positions));
    }
    return elections;
  }

  /**
   * Formats a list of positions and their associated candidates' information. Correlates
   * a {@code Position} object with one or more {@code DirectoryCandidate}. Candidates running
   * for the same position are assumed to be consecutive in {@code candidateIds} and
   * {@code candidateIncumbency}.
   */
  private List<Position> extractPositionInformation(List<String> candidatePositions,
      List<String> candidateIds, List<Boolean> candidateIncumbency) {
    System.out.println(candidatePositions);
    Set<String> distinctPositions = new HashSet<>(candidatePositions);
    List<Position> positions = new ArrayList<>(distinctPositions.size());
    for (String positionName : distinctPositions) {
      int startIndex = candidatePositions.indexOf(positionName);
      int endIndex = candidatePositions.lastIndexOf(positionName);
      List<DirectoryCandidate> candidates = new ArrayList<>(endIndex - startIndex + 1);
      for (int i = startIndex; i <= endIndex; i++) {
        candidates.add(extractCandidateInformation(candidateIds.get(i),
                                                   candidateIncumbency.get(i)));
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

  /**
   * Takes an {@code Entity} object which represents an election, and returns true if
   * this election is deemed relevant to the given {@code address}. If the {@code listAll}
   * parameter is true, then the election is always deemed relevant.
   */
  private boolean isRelevantElection(Entity election, String address, boolean listAllElections) {
    if (listAllElections) {
      return true;
    } else {
      // TODO: Add code which uses the Civic Information API to determine whether a given
      // election is relevant.
      return false;
    }
  }

  // Class to package together different types of data as a HTTP response.
  class DirectoryPageDataPackage {
    private List<Election> electionsData;

    DirectoryPageDataPackage(List<Election> electionsData) {
      this.electionsData = electionsData;
    }
  }
}
