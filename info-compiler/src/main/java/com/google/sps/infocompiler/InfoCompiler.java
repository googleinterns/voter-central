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
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final String ELECTION_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/elections?key=%s",
                    Config.CIVIC_INFO_API_KEY);
  private static final String VOTER_INFO_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                    Config.CIVIC_INFO_API_KEY);
  static final String TEST_VIP_ELECTION_QUERY_ID = "2000";
  private static final Pattern STATE_PATTERN = Pattern.compile(".*state:(..).*");
  private static final long QUOTA_TIME_UNIT_MILLISECONDS = 100 * 1000;
  private static final int QUOTA_QUERY_LIMIT = 250;
  private static final long QUERY_PAUSE_MILISECONDS =
      (long) (QUOTA_TIME_UNIT_MILLISECONDS / QUOTA_QUERY_LIMIT * Config.PAUSE_FACTOR);
  Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
  WebCrawler webCrawler;
  List<String> electionQueryIds;
  // List of U.S. street addresses that theoretically cover the entire U.S.
  List<String> addresses;

  public InfoCompiler() throws IOException {
    this(DatastoreOptions.getDefaultInstance().getService());
  }

  /** For testing purposes. */
  public InfoCompiler(Datastore datastore) throws IOException {
    this.datastore = datastore;
    this.webCrawler =
        new WebCrawler(this.datastore, new NewsContentExtractor(), new RelevancyChecker());
    parseAddressesFromDataset();
  }

  /**
   * Reads the National Address Database (Release 3), referenced below, from Google Cloud Storage.
   * The file is located at bucket {@code Config.ADDRESS_BUCKET_NAME} and named {@code
   * Config.ADDRESS_FILE_NAME}.
   *
   * @see <a href=
   *    "https://www.transportation.gov/gis/national-address-database/national-address-database-0">
   *    National Address Database (Release 3)</a>
   */
  private void parseAddressesFromDataset() {
    Storage storage =
        StorageOptions.newBuilder().setProjectId(Config.PROJECT_ID).build().getService();
    String[] addressFile =
        new String (storage.get(Config.ADDRESS_BUCKET_NAME, Config.ADDRESS_FILE_NAME,
                                Storage.BlobGetOption.userProject(Config.PROJECT_ID))
                        .getContent()).split("\\r?\\n");
    addresses = new ArrayList<>(addressFile.length);
    for (String fullAddress : addressFile) {
      // Extract the full address and discard other data, such as coordinates or ill-formated data.
      String[] fullAddressSplit = fullAddress.split(",,,,,,,,,,");
      if (fullAddressSplit.length == 2) {
        addresses.add(fullAddressSplit[0]);
      }
    }
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
  void queryAndStoreBaseElectionInfo() {
    queryAndStore(ELECTION_QUERY_URL, "elections", null);
  }

  /**
   * Queries the VoterInfoQuery of the Civic Information API for election positions and candidates
   * information, which will serve as the starting point for finding additional information, and
   * stores said found information in the database. VoterInfoQuery requires two query parameters:
   * (1) address and (2) election ID. To cover the entire United States and all elections, queries
   * all combinations of {@code addresses} and election IDs. Information includes: candidate names
   * and candidate party affiliations. Pauses for {@code QUERY_PAUSE_MILISECONDS} per query to
   * respect the query rate limit of the Civic Information API.
   */
  void queryAndStoreElectionContestInfo() {
    int addressStartIndex = Math.max(0, Config.ADDRESS_START_INDEX);
    int addressEndIndex = Math.min(addresses.size(), Config.ADDRESS_END_INDEX);
    for (String address : addresses.subList(addressStartIndex, addressEndIndex)) {
      for (String electionQueryId : electionQueryIds) {
        try {
          queryAndStoreElectionContestInfo(address, electionQueryId);
          pause(QUERY_PAUSE_MILISECONDS);
        } catch (UnsupportedEncodingException e) {}
      }
    }
  }

  /**
   * Waits for {@code timeToPause} milliseconds if necessary and returns true if the pause
   * succeeded.
   */
  private boolean pause(long timeToPause) {
    try {
      TimeUnit.MILLISECONDS.sleep(timeToPause);
    } catch (InterruptedException e) {
      return false;
    }
    return true;
  }

  /**
   * Queries the ElectionQuery of the Civic Information API (once) for the election positions and
   * candidates information of a particular {@code address} and election corresponding to {@code
   * electionQueryId}, and stores said found information in the database.
   *
   * @throws UnsupportedEncodingException if {@code address} cannot be encoded into a valid URL.
   */
  void queryAndStoreElectionContestInfo(String address, String electionQueryId)
      throws UnsupportedEncodingException {
    String queryUrl =
        String.format("%s&address=%s&electionId=%s", VOTER_INFO_QUERY_URL,
                      URLEncoder.encode(address, "UTF-8"),
                      electionQueryId);
    queryAndStore(queryUrl, "contests", electionQueryId);
  }

  /**
   * Queries the Civic Information API (once) for {@code targetInfo}, by making requests to {@code
   * queryUrl}, and saves found information in the database.
   */
  void queryAndStore(String queryUrl, String targetInfo, String electionQueryId) {
    JsonArray infoArray;
    try {
      infoArray = queryCivicInformation(queryUrl).getAsJsonArray(targetInfo);
      if (infoArray == null) {
        return;
      }
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
  JsonObject queryCivicInformation(String queryUrl) throws IOException {
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
   * default, stores the election day at the beginning of the day in EDT timezone. Extracts the
   * state name from the political division information.
   */
  void storeBaseElectionInDatabase(JsonObject election) {
    String electionQueryId = election.get("id").getAsString();
    if (electionQueryId.equals(TEST_VIP_ELECTION_QUERY_ID)) {
      return;
    }
    Key electionKey = datastore.newKeyFactory()
        .setKind("Election")
        .newKey(election.get("name").getAsString());
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date =
        new Date(
            Integer.parseInt(yearMonthDay[0]) - 1900,
            Integer.parseInt(yearMonthDay[1]) - 1,
            Integer.parseInt(yearMonthDay[2]),
            4,
            0); // Convert from UTC to EDT with 4 hours.
    // Extract state name for the election. Set state to empty if not found.
    String division = election.get("ocdDivisionId").getAsString();
    Matcher stateFinder = STATE_PATTERN.matcher(division);
    String state = stateFinder.find() ? stateFinder.group(1).toUpperCase() : "";
    Entity electionEntity =
        Entity.newBuilder(electionKey)
            .set("queryId", electionQueryId)
            .set("date", TimestampValue.newBuilder(Timestamp.of(date)).build())
            .set("candidatePositions", Arrays.asList())
            .set("candidateIds", Arrays.asList())
            .set("candidateIncumbency", Arrays.asList())
            .set("state", state)
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
    // Obtain candidate information and create candidate entities in the database.
    for (JsonElement candidate : candidates) {
      storeElectionContestCandidateInDatabase(
          (JsonObject) candidate,
          candidateIds,
          candidateIncumbency);
      candidatePositions.add(StringValue.newBuilder(contest.get("office").getAsString()).build());
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

  // @TODO [Find incumbency.]
  /**
   * Stores information of a {@code candidate} in the database, and updates the candidate's running
   * position's information for the election. Information includes: name, party affiliation,
   * incumbency status, and news articles related to the candidate.
   */
  void storeElectionContestCandidateInDatabase(JsonObject candidate,
      List<Value<String>> candidateIds, List<Value<Boolean>> candidateIncumbency) {
    String name = candidate.get("name").getAsString();
    String party = candidate.get("party").getAsString();
    // @TODO [May expand to other information to uniquely identify a candidate. Currently,
    // candidate information includes only name and party affiliation.]
    long candidateId = (long) (name.hashCode() + party.hashCode());
    Key candidateKey =
        datastore.newKeyFactory()
            .setKind("Candidate")
            .newKey(candidateId);
    Entity candidateEntity =
        Entity.newBuilder(candidateKey)
            .set("name", name)
            .set("partyAffiliation", party + " Party")
            .build();
    datastore.put(candidateEntity);
    candidateIds.add(StringValue.newBuilder(Long.toString(candidateId)).build());
    candidateIncumbency.add(BooleanValue.newBuilder(false).build());

    compileAndStoreCandidateNewsArticlesInDatabase(name, new Long(candidateId).toString());
  }

  /**
   * Compiles news articles data of {@code candidateName} and stores said data in the database.
   * News articles data are represented by {@code NewsArticle}.
   */
  private void compileAndStoreCandidateNewsArticlesInDatabase(String candidateName,
      String candidateId) {
    webCrawler.compileNewsArticle(candidateName, candidateId);
  }
}
