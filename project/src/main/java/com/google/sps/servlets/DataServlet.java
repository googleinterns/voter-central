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
import com.google.sps.data.Election;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/data")
public class DataServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Extract user input of an address.
    String address = request.getParameter("address");

    // Resolve {@code address} to a list of election names.
    // @TODO [Need to call the Civic Information API]
    List<String> electionNames = Arrays.asList("New York's 14th Congressional District Election");

    // Find election/candidate information. Package and convert the data to JSON.
    List<Election> electionsData = findBriefElectionCandidateInformation(electionNames);
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
   * Queries the database for (brief version) official election/candidate information and format
   * as {@code Election} objects.
   */
  private List<Election> findBriefElectionCandidateInformation(List<String> electionNames) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    // Query for (brief version) official election/candidate information, with election names.
    List<Entity> elections = new ArrayList<>(electionNames.size());
    for (String electionName : electionNames) {
      Query electionQuery = new Query("Election")
          .setFilter(new FilterPredicate("__key__", FilterOperator.EQUAL,
                    KeyFactory.createKey("Election", electionName)));
      PreparedQuery electionQueryResult = datastore.prepare(electionQuery);
      elections.add(electionQueryResult.asSingleEntity());
    }
    List<Election> electionsData = new ArrayList<>(elections.size());
    for (Entity election : elections) {
      String name = election.getKey().getName();
      Date date = (Date) election.getProperty("date");
      List<String> candidateIds = (List<String>) election.getProperty("candidateIds");
      List<String> candidateNames = new ArrayList<>(candidateIds.size());
      List<String> candidatePositions = (List<String>) election.getProperty("candidatePositions");
      List<String> candidatePartyAffiliation = new ArrayList<>(candidateIds.size());
      List<String> candidateIncumbency= (List<String>) election.getProperty("candidateIncumbency");
      // Query for a subset of candidate information.
      for (String candidateId : candidateIds) {
        Query candidateQuery = new Query("Candidate")
            .setFilter(new FilterPredicate("__key__", FilterOperator.EQUAL,
                       KeyFactory.createKey("Candidate", Long.parseLong(candidateId))));
        PreparedQuery candidateQueryResult = datastore.prepare(candidateQuery);
        Entity candidate = candidateQueryResult.asSingleEntity();
        candidateNames.add((String) candidate.getProperty("name"));
        candidatePartyAffiliation.add((String) candidate.getProperty("partyAffiliation"));
      }
      Election electionData = new Election(name, date, candidateIds, candidateNames,
                                           candidatePositions, candidatePartyAffiliation,
                                           candidateIncumbency);
      electionsData.add(electionData);
    }
    return electionsData;
  }

  // Class to package together different types of data as a HTTP response.
  class DirectoryPageDataPackage {
    private List<Election> electionsData;

    DirectoryPageDataPackage(List<Election> inputElectionsData) {
      this.electionsData = inputElectionsData;
    }
  }
}
