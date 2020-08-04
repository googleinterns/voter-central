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

package com.google.sps.infocompiler;

import com.google.cloud.datastore.BooleanValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.TimestampValue;
import com.google.cloud.datastore.Value;
import com.google.cloud.Timestamp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.sps.infocompiler.Config;
import com.google.sps.webcrawler.NewsContentExtractor;
import com.google.sps.webcrawler.RelevancyChecker;
import com.google.sps.webcrawler.WebCrawler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * A information compiler for finding (1) official election/candidate information and (2) news
 * article information, and storing them in the database.
 */
public class InfoCompiler {
  private final static String ELECTION_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/elections?key=%s",
                    Config.CIVIC_INFO_API_KEY);
  private final static String VOTER_INFO_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                    Config.CIVIC_INFO_API_KEY);
  private final static String REPRENTATIVE_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/representatives?key=%s",
                    Config.CIVIC_INFO_API_KEY);
  private Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
  private List<String> electionQueryIds = new ArrayList<>();
  // For testing purposes (not to add too much information to the database).
  // Will include all 50 states.
  private List<String> states = Arrays.asList("NY");
  private WebCrawler webCrawler;

  public InfoCompiler() throws IOException {
    this(DatastoreOptions.getDefaultInstance().getService());
  }

  /** For testing purposes. */
  public InfoCompiler(Datastore datastore) throws IOException {
    this.datastore = datastore;
    this.webCrawler =
        new WebCrawler(this.datastore, new NewsContentExtractor(), new RelevancyChecker());
  }

  /**
   * Compiles location-specific information for elections, positions and candidates.
   */
  public void compileInfo() {
    queryAndStoreBaseElectionInfo();
    queryAndStoreElectionContestInfo();
  }

  /**
   * Queries the ElectionQuery of the Civic Information API for a basic subset of election
   * information, which will serve as the starting point for finding additional information, and
   * stores said found information in the database. Information includes: name, date, and query ID
   * of the election for the Civic Information API.
   */
  public void queryAndStoreBaseElectionInfo() {
    queryAndStore(ELECTION_QUERY_URL, "elections", null);
  }

  /**
   * Queries the VoterInfoQuery of the Civic Information API for election positions and candidates
   * information, which will serve as the starting point for finding additional information, and
   * stores said found information in the database. VoterInfoQuery requires two query parameters:
   * (1) address and (2) election ID. To cover the entire United States and all elections, queries
   * all combinations of states as the address and election IDs. Information includes: positions,
   * candidate names, candidate party affiliations.
   */
  public void queryAndStoreElectionContestInfo() {
    for (String state : states) {
      for (String electionQueryId : electionQueryIds) {
        queryAndStoreElectionContestInfo(state, electionQueryId);
      }
    }
  }

  /**
   * Queries the ElectionQuery of the Civic Information API (once) for the election positions and
   * candidates information of a particular state and election, and stores said found information
   * in the database.
   */
  private void queryAndStoreElectionContestInfo(String state, String electionQueryId) {
    String queryUrl =
        String.format("%s&address=%s&electionId=%s", VOTER_INFO_QUERY_URL, state, electionQueryId);
    queryAndStore(queryUrl, "contests", electionQueryId);
  }

  /**
   * Queries the Civic Information API (once) for {@code targetInfo}, by making requests to {@code
   * queryUrl}, and saves found information in the database.
   */
  private void queryAndStore(String queryUrl, String targetInfo, String electionQueryId) {
    JsonArray infoArray;
    try {
      infoArray = queryCivicInformation(queryUrl).getAsJsonArray(targetInfo);
    } catch (IOException e) {
      System.out.println(
          String.format(
              "[ERROR] Failed to query the Civic Information API for %s: %s.", targetInfo, e));
      return;
    }
    if (targetInfo.equals("elections")) {
      electionQueryIds = new ArrayList<>(infoArray.size());
      for (JsonElement info : infoArray) {
        storeBaseElectionInDatabase((JsonObject) info);
      }
    } else if (targetInfo.equals("contests")) {
      for (JsonElement info : infoArray) {
        storeElectionContestInDatabase(electionQueryId, (JsonObject) info);
      }
    }
  }

  /** 
   * Queries the Civic Information API and retrieves JSON response as {@code JsonObject}.
   *
   * @throws ClientProtocolException if the GET request to the Civic Information API fails.
   */
  private JsonObject queryCivicInformation(String queryUrl) throws IOException {
    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet httpGet = new HttpGet(queryUrl);
    JsonObject json = requestHttpAndBuildJsonResponse(httpClient, httpGet);
    httpClient.close();
    return json;
  }

  /** 
   * Makes HTTP GET request and converts HTTP JSON response as {@code JsonObject}.
   *
   * @throws ClientProtocolException if the HTTP GET request fails.
   * @see <a href=
   *    "https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/" +
   *    "http/examples/client/ClientWithResponseHandler.java">Code reference</a>
   */
  public static JsonObject requestHttpAndBuildJsonResponse(
      CloseableHttpClient httpClient, HttpGet httpGet) throws IOException {
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
    String responseBody = httpClient.execute(httpGet, responseHandler);
    return new JsonParser().parse(responseBody).getAsJsonObject();
  }

  /**
   * Stores the name, date and query ID of {@code election} in to the database. The original format
   * of the election day is "YYYY-MM-DD", and the specific hour/minute/second is irrelevant. By
   * default, stores the election day at the beginning of the day in EDT timezone.
   */
  void storeBaseElectionInDatabase(JsonObject election) {
    Key electionKey = datastore.newKeyFactory()
        .setKind("Election")
        .newKey(election.get("name").getAsString());
    String electionQueryId = election.get("id").getAsString();
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date =
        new Date(
            Integer.parseInt(yearMonthDay[0]) - 1900,
            Integer.parseInt(yearMonthDay[1]) - 1,
            Integer.parseInt(yearMonthDay[2]),
            4,
            0); // Convert from UTC to EDT with 4 hours.
    String ocdDivisionId = election.get("ocdDivisionId").getAsString();
    Entity electionEntity =
        Entity.newBuilder(electionKey)
            .set("queryId", electionQueryId)
            .set("date", TimestampValue.newBuilder(Timestamp.of(date)).build())
            .set("ocdDivisionId", ocdDivisionId)
            .set("candidatePositions", Arrays.asList())
            .set("candidateIds", Arrays.asList())
            .set("candidateIncumbency", Arrays.asList())
            .build();
    datastore.put(electionEntity);
    electionQueryIds.add(electionQueryId);
  }

  /**
   * Stores the {@code contest} information of an election, including election positions, running
   * candidate names and party affiliations. Updates {@code Election} entities and creates {@code
   * Candidate} entities.
   */
  void storeElectionContestInDatabase(String electionQueryId, JsonObject contest) {
    JsonArray candidates = contest.getAsJsonArray("candidates");
    if (candidates == null) {
      return;
    }
    Query<Entity> electionQuery =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .setFilter(PropertyFilter.eq("queryId", electionQueryId))
            .build();
    QueryResults<Entity> electionQueryResults = datastore.run(electionQuery);
    Entity electionEntity = electionQueryResults.next();
    // Create a copy since the original list is a
    // {@code com.google.common.collect.ImmutableCollection}.
    List<Value<String>> candidatePositions =
        new ArrayList<>(electionEntity.getList("candidatePositions"));
    List<Value<String>> candidateIds =
        new ArrayList<>(electionEntity.getList("candidateIds"));
    List<Value<Boolean>> candidateIncumbency =
        new ArrayList<>(electionEntity.getList("candidateIncumbency"));
    String ocdDivisionId = electionEntity.getString("ocdDivisionId");
    Map<String, List<String>> incumbents = getIncumbents(ocdDivisionId);
    
    // Obtain candidate information and create candidate entities in the database.
    for (JsonElement candidate : candidates) {
      Value<String> position = StringValue.newBuilder(contest.get("office").getAsString()).build();
      storeElectionContestCandidateInDatabase(
          (JsonObject) candidate,
          candidateIds,
          candidateIncumbency,
          candidatePositions,
          position,
          incumbents);
    }
    // Fill in position and candidate information for the election entities in the database.
    electionEntity =
        Entity.newBuilder(electionEntity)
            .set("candidatePositions", candidatePositions)
            .set("candidateIds", candidateIds)
            .set("candidateIncumbency", candidateIncumbency)
            .build();
    datastore.update(electionEntity);
  }

  /**
   * Queries the API for the representatives by division JSON. then returns all representatives in
   * a map that can be checked to verify incumbency.
   */
  private Map<String, List<String>> getIncumbents(String division) {
    try {
      String queryUrl = String.format("%s&ocdDivisionId=%s", REPRENTATIVE_QUERY_URL, division);
      JsonObject representatives = queryCivicInformation(queryUrl);
      return getIncumbents(representatives);
    } catch (IOException e) {
        System.out.println(
          String.format(
              "[ERROR] Failed to query the Civic Information API for %s: %s.", "representatives", e));
      return new HashMap<String, List<String>>();
    }
  }

  Map<String, List<String>> getIncumbents(JsonObject representatives) {
    Map<String, List<String>> mapOfIncumbents = new HashMap<>();  
    JsonArray offices = representatives.getAsJsonArray("offices");
    JsonArray officials = representatives.getAsJsonArray("officials");
    for (JsonElement eachOffice : offices) {
      JsonObject office = (JsonObject) eachOffice;
      String officeName = office.get("name").getAsString();
      JsonArray officialIndicesArray =  office.getAsJsonArray("officialIndices");
      List<String> incumbents = new ArrayList<>();
      for (JsonElement index : officialIndicesArray) {
        incumbents.add((officials.get(index.getAsInt())).getAsString());
      }
      mapOfIncumbents.put(officeName, incumbents);
    }
    return mapOfIncumbents;
  }

  /**
   * Stores information of a {@code candidate} in the database, and updates the candidate's running
   * position's information for the election. Information includes: name, party affiliation,
   * incumbency status, and news articles related to the candidate.
   */
  private void storeElectionContestCandidateInDatabase(JsonObject candidate,
      List<Value<String>> candidateIds, List<Value<Boolean>> candidateIncumbency, 
      List<Value<String>> candidatePositions, Value<String> position, Map<String, List<String>> incumbents) {
    String name = candidate.get("name").getAsString();
    String party = candidate.get("party").getAsString();
    String email = getField(candidate, "email");
    String phone = getField(candidate, "phone");
    String photoUrl = getField(candidate, "PhotoUrl");
    String candidateUrl = getField(candidate, "candidateUrl");
    String twitter = getTwitter(candidate);
    long candidateId = (long) (name.hashCode() + party.hashCode());
    Key candidateKey =
        datastore.newKeyFactory()
            .setKind("Candidate")
            .newKey(candidateId);
    Entity candidateEntity =
        Entity.newBuilder(candidateKey)
            .set("name", name)
            .set("party", party + " Party")
            .set("email", email)
            .set("phone", phone)
            .set("PhotoUrl", photoUrl)
            .set("candidateUrl", candidateUrl)
            .set("twitter", twitter)
            .build();
    datastore.put(candidateEntity);
    candidateIds.add(StringValue.newBuilder(Long.toString(candidateId)).build());
    boolean isIncumbent = incumbents.containsKey(position) && incumbents.get(position).contains(name);
    candidateIncumbency.add(BooleanValue.newBuilder(isIncumbent).build());
    candidatePositions.add(position);
    compileAndStoreCandidateNewsArticlesInDatabase(name, new Long(candidateId).toString());
  }
  
  // Checks JSON for desired field then returns the Field as a String
  private String getField(JsonObject candidate, String field) {
    if (candidate.has(field)) {
      return candidate.get(field).getAsString();
    } else {
      return "";
    }
  }

  // Checks JSON for Twittter Handle which is nested in Channels Array then returns as String
  private String getTwitter(JsonObject candidate) {
    if (candidate.has("channels")){
      JsonArray channels = candidate.getAsJsonArray("channels");
      for (JsonElement eachChannel : channels) {
        JsonObject channel = (JsonObject) eachChannel;
        String channelType = channel.get("type").getAsString();
        if (channelType.equals("Twitter")){
          return channel.get("id").getAsString();
        }
      }
    }
    return ""; 
  }

  /**
   * Compiles news articles data of {@code candidateName} and stores said data in the database.
   * News articles data are represented by {@code NewsArticle}.
   */
  private void compileAndStoreCandidateNewsArticlesInDatabase(String candidateName,
      String candidateId) {
    try {
      webCrawler.compileNewsArticle(candidateName, candidateId);
    } catch (Exception e) {}
  }
}
