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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.sps.data.DirectoryCandidate;
import com.google.sps.data.Election;
import com.google.sps.data.Position;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.lang.Boolean;
import java.net.SocketException;
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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/** 
 * Queries the backend database and serves location-based search results of (brief)
 * election/candidate information for the directory page.
 */
@WebServlet("/data")
public class DataServlet extends HttpServlet {
  private final static String VOTER_INFO_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s", Config.CIVIC_INFO_API_KEY);
  private final static String RELEVANT_NONSPECIFIC_ADDRESS_ALERT =
      "Your input address was not specific enough or was not a residential address.\n" + 
      "We are providing all possible elections that may be relevant.";
  boolean isAddressRelevantButNotSpecificOrResidential;

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String address = request.getParameter("address");
    boolean listAllElections = Boolean.parseBoolean(request.getParameter("listAllElections"));
    String stateFilter = request.getParameter("stateFilter");

    // Find election/candidate information. Package and convert the data to JSON.
    List<Election> elections = extractElectionInformation(address, listAllElections, stateFilter);
    DirectoryPageDataPackage dataPackage =
        new DirectoryPageDataPackage(elections,
                                     isAddressRelevantButNotSpecificOrResidential
                                     ? RELEVANT_NONSPECIFIC_ADDRESS_ALERT
                                     : null);
    Gson gson = new Gson();
    String dataPackageJson = gson.toJson(dataPackage);

    // Send data in JSON format as the servlet response for the directory page.
    response.setContentType("application/json;");
    response.getWriter().println(dataPackageJson);
  }

  /**
   * Resets the flag for whether the user input address is relevant but nonspecific.
   */
  private void resetRelevantNonspecificFlag() {
    this.isAddressRelevantButNotSpecificOrResidential = false;
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {}

  /**
   * Queries the database for (brief version) official election/position/candidate information and
   * formats the data as {@code Election} objects. Correlates one {@code Election} with one or more
   * {@code Position}.
   */
  private List<Election> extractElectionInformation(String address, boolean listAllElections,
      String stateFilter) {
    List<Election> elections = new ArrayList<>();
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query electionQuery = new Query("Election");
    PreparedQuery electionQueryResult = datastore.prepare(electionQuery);
    List<Entity> electionsData = electionQueryResult.asList(FetchOptions.Builder.withDefaults());
    for (Entity election : electionsData) {
      if (!isRelevantElection(election, address, listAllElections, stateFilter)) {
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
   * Takes an {@code Entity} object which represents an election, and returns true if this election
   * is deemed relevant to the given {@code address}. If the {@code listAllElections} parameter is
   * true, filter elections based on {@code stateFilter}. If {@code stateFilter} is null, meaning
   * that the filter was not set, or if the {@code election} does not correlate with a particular
   * state, returns true. Otherwise returns true if the state name mathces {@code stateFilter}.
   */
  boolean isRelevantElection(Entity election, String address, boolean listAllElections,
      String stateFilter) {
    resetRelevantNonspecificFlag();
    if (listAllElections) {
      String state = (String) election.getProperty("state");
      return (stateFilter == null || state.isEmpty()) ? true : state.equals(stateFilter);
    } else {
      // Query the Civic Information API for whether {@code election} is relevant to
      // {@code address}.
      String queryUrl =
        String.format("%s&address=%s&electionId=%s&officialOnly=true", VOTER_INFO_QUERY_URL,
                      URLEncoder.encode(address),
                      (String) election.getProperty("queryId"));
      String addressElectionResponse;
      try {
        addressElectionResponse = queryCivicInformation(queryUrl);
      } catch (Exception e) {
        return false;
      }
      parseResponseForRelevancy(addressElectionResponse);
      return true;
    }
  }

  /**
   * Queries the Civic Information API and retrieves JSON response as a String.
   *
   * @throws ClientProtocolException if the GET request to the Civic Information API fails.
   * @throws SocketException if {@code queryUrl} is ill-constructed, such as with a null value
   *     as the election query ID, causing {@code httpclient.execute(httpGet, responseHandler);}
   *     to fail.
   */
  String queryCivicInformation(String queryUrl) throws IOException, SocketException {
    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet httpGet = new HttpGet(queryUrl);
    String responseBody = requestHttpAndBuildCivicInfoResponse(httpClient, httpGet);
    httpClient.close();
    return responseBody;
  }

  /**
   * Makes HTTP GET request to the Civic Information API amd returns HTTP JSON response as a
   * String. This method is given default visibility for testing purposes.
   *
   * @throws ClientProtocolException if the GET request to the Civic Information API fails.
   * @throws SocketException if {@code queryUrl} is ill-constructed, such as with a null value
   *     as the election query ID, causing {@code httpclient.execute(httpGet, responseHandler);}
   *     to fail.
   * @see <a href=
   *    "https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/" +
   *    "http/examples/client/ClientWithResponseHandler.java">Code reference</a>
   */
  String requestHttpAndBuildCivicInfoResponse(CloseableHttpClient httpClient,
      HttpGet httpGet) throws IOException, SocketException {
    ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
        @Override
        public String handleResponse(final HttpResponse response) throws IOException {
          int status = response.getStatusLine().getStatusCode();
          if (status >= 200 && status < 300) {
            HttpEntity entity = response.getEntity();
            return entity != null ? EntityUtils.toString(entity) : null;
          } else {
            throw new ClientProtocolException("Unexpected response status: " + status);
          }
        }
    };
    return httpClient.execute(httpGet, responseHandler);
  }

  /**
   * Parses JSON {@code addressElectionResponse} and checks if the input address is specific enough
   * and counts as a residential address so that contests information is available: @see <a href=
   * "https://developers.google.com/civic-information/docs/using_api#voterinfoquery-response:">
   * response structure</a>. This method is given default visibility for testing purposes.
   */
  void parseResponseForRelevancy(String addressElectionResponse) {
    JsonObject responseJson = new JsonParser().parse(addressElectionResponse).getAsJsonObject();
    if (!responseJson.has("contests")) {
      isAddressRelevantButNotSpecificOrResidential = true;
    }
  }

  /**
   * Formats a list of positions and their associated candidates' information. Correlates
   * a {@code Position} object with one or more {@code DirectoryCandidate}. Candidates running
   * for the same position are assumed to be consecutive in {@code candidateIds} and
   * {@code candidateIncumbency}.
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

  // Class to package together different types of data as a HTTP response.
  class DirectoryPageDataPackage {
    private List<Election> electionsData;
    private String alert;

    DirectoryPageDataPackage(List<Election> electionsData, String alert) {
      this.electionsData = electionsData;
      this.alert = alert;
    }
  }
}
