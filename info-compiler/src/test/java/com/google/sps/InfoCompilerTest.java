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

import static com.google.common.truth.Truth.*;
import static com.google.common.truth.Truth8.*;
import static org.mockito.Mockito.*;

import com.google.cloud.datastore.BooleanValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Value;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.testing.LocalDatastoreHelper;
import com.google.cloud.datastore.Value;
import com.google.cloud.Timestamp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.sps.webcrawler.WebCrawler;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/**
 * A tester for the location-based information compiler.
 * (It's recommended to run InfoCompilerTest indenpendently, not together with other tests in the
 * package that use Datastore emulators. There is instability with Datastore emulators, potentially
 * due to HTTP communication.)
 */
@RunWith(JUnit4.class)
public final class InfoCompilerTest {
  private static final int ADDRESS_NUMBER = 957; // After screening.
  private static String CORRECT_FORMAT_NAME = "Andrew Cuomo";
  private static String CORRECT_FORMAT_NAME_IN_QUOTES = "\"Andrew Cuomo\"";
  private static final String PARTY = "Democratic";
  private static final String EMAIL = "ac@gmail.com";
  private static final String PHOTO_URL = "photoOfCuomo.jpg";
  private static final String CANDIDATE_URL = "www.andrewcuomo.com";
  private static final String PHONE = "122-333-4444";
  private static final String TWITTER_ID = "@thefakeACuomo";
  private static final String OFFICE = "Governor";
  private static final String DIVISION = "ocd-division/country:us";
  private static final String ADDRESS = ",NY,New York,,,,,10028,,,,,East,,,84,Street,,,,144";
  private static final String STATE = "NY";
  private static final String NONTEST_ELECTION_QUERY_ID =
      InfoCompiler.TEST_VIP_ELECTION_QUERY_ID + "0";
  private static final String CIVIC_INFO_API_KEY = Config.CIVIC_INFO_API_KEY;
  private static final boolean PLACEHOLDER_INCUMBENCY = true;
  private static final String ELECTION_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/elections?key=%s",
                    CIVIC_INFO_API_KEY);
  private static final String VOTER_INFO_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                    Config.CIVIC_INFO_API_KEY);
  private static final String REPRENTATIVE_QUERY_URL_WITHOUT_KEY =
      "https://www.googleapis.com/civicinfo/v2/representatives";
  private static final String CONTEST_QUERY_URL =
        String.format("%s&address=%s&electionId=%s", VOTER_INFO_QUERY_URL,
                      URLEncoder.encode(ADDRESS), NONTEST_ELECTION_QUERY_ID);
  // @see <a href=
  //     "https://developers.google.com/civic-information/docs/using_api#electionquery-example">
  //     Sample JSON structure for the Civic Information API</a>
  private static final String ELECTION_RESPONSE =
      "{" +
      " \"kind\": \"civicinfo#electionsqueryresponse\"," +
      " \"elections\": [" +
      "   {" +
      "    \"id\": " + "\"" + NONTEST_ELECTION_QUERY_ID + "\"," +
      "    \"name\": \"VIP Test Election\"," +
      "    \"electionDay\": \"2013-06-06\"," +
      "    \"ocdDivisionId\": " + "\"" + DIVISION + "\"" +
      "   }" +
      " ]" +
      "}";
  private static final String SINGLE_CONTEST_RESPONSE =
      "{" +
      " \"office\": " + OFFICE + "," +
      " \"candidates\": [" +
      "   {" +
      "    \"name\": " + "\"" + CORRECT_FORMAT_NAME + "\"," +
      "    \"party\": " + "\"" + PARTY + "\"," +
      "    \"email\": " + "\"" + EMAIL + "\"," +
      "    \"photoUrl\": " + "\"" + PHOTO_URL + "\"," +
      "    \"candidateUrl\": " + "\"" + CANDIDATE_URL + "\"," +
      "    \"phone\": " + "\"" + PHONE + "\"," +
      "    \"channels\": [" +
      "      {" +
      "        \"type\": \"Twitter\"," +
      "        \"id\": " + "\"" + TWITTER_ID + "\"" +
      "      }," +
      "      {" +
      "        \"type\": \"Facebook\"," +
      "        \"id\": \"randomAccount\"" +
      "      }" +
      "    ]" +
      "   }" +
      " ]" +
      "}";
  private static final String REPRESENTATIVE_RESPONSE = 
      "{" +
      " \"offices\": [" +
      "  {" +
      "   \"name\": " + "\"" + OFFICE + "\"," +
      "   \"officialIndices\": [" +
      "      0" +
      "    ]" +
      "  }" +
      " ]," +
      " \"officials\": [" +
      "  {" +
      "   \"name\": " + "\"" + CORRECT_FORMAT_NAME + "\"" +
      "  }," +
      "  {" +
      "   \"name\": \"John Doe\"" +
      "  }" +
      " ]" +
      "}";
  private static final JsonObject electionJson =
      new JsonParser().parse(ELECTION_RESPONSE).getAsJsonObject();
  private static JsonObject singleContestJson =
      new JsonParser().parse(SINGLE_CONTEST_RESPONSE).getAsJsonObject();
  private static final JsonObject representativesJson =
      new JsonParser().parse(REPRESENTATIVE_RESPONSE).getAsJsonObject();

  private static Map<String, List<String>> INCUMBENT_MAP = new HashMap<>();
  private static InfoCompiler infoCompiler;
  private static LocalDatastoreHelper datastoreHelper;
  private static Datastore datastore;

  @BeforeClass
  public static void initialize() throws InterruptedException, IOException {
    INCUMBENT_MAP.put(OFFICE, Arrays.asList(CORRECT_FORMAT_NAME));
    datastoreHelper = LocalDatastoreHelper.create();
    datastoreHelper.start();
    datastore = datastoreHelper.getOptions().getService();
    infoCompiler = new InfoCompiler(datastore);
  }

  /**
   * Resets the internal state of the Datastore emulator and then {@code datastore}. Also resets
   * {@code infoCompiler}. We choose to reset, instead of creating/destroying the Datastore
   * emulator at each test, because {@code datastoreHelper.stop()} sometimes generates a {@code
   * java.net.ConnectException}, when making HTTP requests. Hence we try to limit the number of
   * times {@code datastoreHelper} is created/destroyed.
   */
  @Before
  public void resetDatastore() throws IOException {
    datastoreHelper.reset();
    datastore = datastoreHelper.getOptions().getService();
    infoCompiler = new InfoCompiler(datastore);
  }

  @Test
  public void parseAddressesFromDataset_regularParse() {
    // The list of U.S. addresses in the dataset should contains {@code ADDRESS_NUMBER} addresses
    // and contain {@code ADDRESS}.
    assertThat(infoCompiler.addresses).hasSize(ADDRESS_NUMBER);
    assertThat(infoCompiler.addresses).contains(ADDRESS);
  }

  @Test
  public void requestHttpAndBuildJsonResponse_succeedWithMockResponse() throws Exception {
    // Query the Civic Information API with a mock HTTP client + mock callback function of type
    // {@code ResponseHandler<String>} that converts any {@code HttpResponse} response to {@code
    // ELECTION_RESPONSE}, and subsequently convert {@code ELECTION_RESPONSE} to {@code json}.
    // {@code httpGet} is irrelevant in this test, since {@code execute()} is mocked as above.
    // Since String {@code ELECTION_RESPONSE} and JsonObject {@code electionJson} match in
    // content, {@code json} should be exactly the same as {@code electionJson}. Here, we don't
    // repeatedly test the same thing with {@code singleContestJson}.
    CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
    HttpGet httpGet = new HttpGet(ELECTION_QUERY_URL);
    ArgumentCaptor<ResponseHandler<String>> argumentCaptor =
        ArgumentCaptor.forClass(ResponseHandler.class);
    when(httpClient.execute(anyObject(), argumentCaptor.capture())).thenReturn(ELECTION_RESPONSE);
    JsonObject json =
        InfoCompiler.requestHttpAndBuildJsonResponse(httpClient, httpGet);
    assertThat(json).isEqualTo(electionJson);
  }

  @Test
  public void getIncumbents_populateIncumbentsMap() throws Exception {
    // Test that {@code getIncumbents} parses {@code representativesJson} correctly.
    Map<String, List<String>> incumbentMap = infoCompiler.getIncumbents(representativesJson);
    assertThat(incumbentMap).isEqualTo(INCUMBENT_MAP);
  }

  @Test
  public void storeBaseElectionInDatabase_checkDatastoreEntityConstructionFromJsonWithState()
      throws IOException {
    // Parse and re-structure base election information from {@code electionJson}'s
    // "elections" section and store the corresponding entity in the database. Said
    // entity should contain information that is consistent with that in {@code electionJson}.
    // Here state information is added to {@code election} in lower case. {@code infoCompiler}
    // should extract and store the same state, in upper case. A Datastore emulator is used to
    // simulate Datastore operations, as opposed to Mockito mocks.
    Timestamp past = Timestamp.now();
    JsonObject election =
        ((JsonObject) electionJson.getAsJsonArray("elections").get(0)).deepCopy();
    election.addProperty("ocdDivisionId", DIVISION + "/state:" + STATE.toLowerCase());
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date = new Date(
        Integer.parseInt(yearMonthDay[0]) - 1900,
        Integer.parseInt(yearMonthDay[1]) - 1,
        Integer.parseInt(yearMonthDay[2]),
        4,
        0);
    infoCompiler.electionQueryIds = new ArrayList<>();
    infoCompiler.storeBaseElectionInDatabase(election);
    Query<Entity> query =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .build();
    QueryResults<Entity> queryResult = datastore.run(query);
    assertThat(queryResult.hasNext()).isTrue();
    Entity electionEntity = queryResult.next();
    assertThat(queryResult.hasNext()).isFalse();
    assertThat(electionEntity.getKey().getName()).isEqualTo(election.get("name").getAsString());
    assertThat(electionEntity.getString("queryId")).isEqualTo(election.get("id").getAsString());
    assertThat(electionEntity.getTimestamp("date").toDate()).isEqualTo(date);
    assertThat(electionEntity.getList("candidatePositions")).isEmpty();
    assertThat(electionEntity.getList("candidateIds")).isEmpty();
    assertThat(electionEntity.getList("candidateIncumbency")).isEmpty();
    assertThat(electionEntity.getString("state")).isEqualTo(STATE);
    assertThat(((Timestamp) electionEntity.getValue("lastModified").get()).compareTo(past) >= 0)
        .isTrue();
  }

  @Test
  public void storeBaseElectionInDatabase_checkDatastoreEntityConstructionFromJsonWithoutState()
      throws IOException {
    // Parse and re-structure base election information from {@code electionJson}'s
    // "elections" section and store the corresponding entity in the database. Said
    // entity should contain information that is consistent with that in {@code electionJson}.
    // Here no state information is added to {@code election}. So {@code infoCompiler} should
    // store an empty state name. A Datastore emulator is used to simulate Datastore operations, as
    // opposed to Mockito mocks.
    Timestamp past = Timestamp.now();
    JsonObject election =
        ((JsonObject) electionJson.getAsJsonArray("elections").get(0)).deepCopy();
    election.addProperty("ocdDivisionId", DIVISION);
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date = new Date(
        Integer.parseInt(yearMonthDay[0]) - 1900,
        Integer.parseInt(yearMonthDay[1]) - 1,
        Integer.parseInt(yearMonthDay[2]),
        4,
        0);
    infoCompiler.electionQueryIds = new ArrayList<>();
    infoCompiler.storeBaseElectionInDatabase(election);
    Query<Entity> query =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .build();
    QueryResults<Entity> queryResult = datastore.run(query);
    assertThat(queryResult.hasNext()).isTrue();
    Entity electionEntity = queryResult.next();
    assertThat(queryResult.hasNext()).isFalse();
    assertThat(electionEntity.getKey().getName()).isEqualTo(election.get("name").getAsString());
    assertThat(electionEntity.getString("queryId")).isEqualTo(election.get("id").getAsString());
    assertThat(electionEntity.getTimestamp("date").toDate()).isEqualTo(date);
    assertThat(electionEntity.getList("candidatePositions")).isEmpty();
    assertThat(electionEntity.getList("candidateIds")).isEmpty();
    assertThat(electionEntity.getList("candidateIncumbency")).isEmpty();
    assertThat(electionEntity.getString("state")).isEqualTo("");
    assertThat(((Timestamp) electionEntity.getValue("lastModified").get()).compareTo(past) >= 0)
        .isTrue();
  }

  @Test
  public void capitalizeFirstLetterOfEachWord_checkDifferentNames()
      throws IOException {
    // Capitalize the first letter of each word and make the rest of the letters lowercase.
    assertThat(infoCompiler.capitalizeFirstLetterOfEachWord("Andrew Cuomo"))
        .isEqualTo(CORRECT_FORMAT_NAME);
    assertThat(infoCompiler.capitalizeFirstLetterOfEachWord("andrew cuomo"))
        .isEqualTo(CORRECT_FORMAT_NAME);
    assertThat(infoCompiler.capitalizeFirstLetterOfEachWord("ANDREW CUOMO"))
        .isEqualTo(CORRECT_FORMAT_NAME);
  }

  @Test
  public void capitalizeFirstLetterOfEachWord_checkDifferentNamesInQuotes()
      throws IOException {
    // Within the quotation marks, capitalize the first letter of each word and make the rest of
    // the letters lowercase.
    assertThat(infoCompiler.capitalizeFirstLetterOfEachWord("\"Andrew Cuomo\""))
        .isEqualTo(CORRECT_FORMAT_NAME_IN_QUOTES);
    assertThat(infoCompiler.capitalizeFirstLetterOfEachWord("\"andrew cuomo\""))
        .isEqualTo(CORRECT_FORMAT_NAME_IN_QUOTES);
    assertThat(infoCompiler.capitalizeFirstLetterOfEachWord("\"ANDREW CUOMO\""))
        .isEqualTo(CORRECT_FORMAT_NAME_IN_QUOTES);
  }

  @Test
  public void storeElectionContestInDatabase_checkDatastoreEntityConstructionFromJson()
      throws IOException {
    // Parse and re-structure election/position/candidate information from {@code
    // singleContestJson}. Create a correponding candidate entity and update the existing
    // election entity in the database. Said entities should contain information that is consistent
    // with that in {@code singleContestJson}. A Datastore emulator is used to simulate Datastore
    // operations, as opposed to Mockito mocks.
    // This method relies on the election entity created by {@code storeBaseElectionInDatabase()}
    // and thus assumes the correctness of said method. This method avoids repeating any tests
    // executed by {@code storeBaseElectionInDatabase_checkDatastoreEntityConstructionFromJson()}.
    Timestamp past = Timestamp.now();
    JsonObject election =
        ((JsonObject) electionJson.getAsJsonArray("elections").get(0)).deepCopy();
    String ocdDivisionId = DIVISION + "/state:" + STATE.toLowerCase();
    election.addProperty("ocdDivisionId", ocdDivisionId);
    String representativesQuery =
          String.format("%s/%s?key=%s", REPRENTATIVE_QUERY_URL_WITHOUT_KEY,
              URLEncoder.encode(ocdDivisionId), Config.CIVIC_INFO_API_KEY);
    InfoCompiler infoCompiler = new InfoCompiler(this.datastore);
    InfoCompiler infoCompilerSpy = spy(infoCompiler);
    doReturn(representativesJson).when(infoCompilerSpy).queryCivicInformation(eq(representativesQuery));
    JsonObject candidate = (JsonObject) singleContestJson.getAsJsonArray("candidates").get(0);
    Long candidateId = new Long(candidate.get("name").getAsString().hashCode()
                                + candidate.get("party").getAsString().hashCode()
                                + election.get("name").getAsString().hashCode());
    infoCompilerSpy.electionQueryIds = new ArrayList<>();
    infoCompilerSpy.storeBaseElectionInDatabase(election);
    infoCompilerSpy.storeElectionContestInDatabase(election.get("id").getAsString(),
                                                singleContestJson);

    // Check data additions to the election entity.
    Query<Entity> electionQuery =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .build();
    Entity electionEntity = datastore.run(electionQuery).next();
    List<Value<String>> candidatePositions =
        new ArrayList<>(electionEntity.getList("candidatePositions"));
    assertThat(candidatePositions).hasSize(1);
    assertThat(candidatePositions)
        .containsExactly(StringValue.newBuilder(
                         singleContestJson.get("office").getAsString()).build());
    List<Value<String>> candidateIds =
        new ArrayList<>(electionEntity.getList("candidateIds"));
    assertThat(candidateIds).hasSize(1);
    assertThat(candidateIds)
        .containsExactly(StringValue.newBuilder(candidateId.toString()).build());
    List<Value<Boolean>> candidateIncumbency =
        new ArrayList<>(electionEntity.getList("candidateIncumbency"));
    assertThat(candidateIncumbency).hasSize(1);
    assertThat(candidateIncumbency)
        .containsExactly(BooleanValue.newBuilder(PLACEHOLDER_INCUMBENCY).build());
    System.out.println(candidateIncumbency);
    System.out.println("!!!!!!!!!!!!!");
    // Check candidate data.
    Query<Entity> candidateQuery =
        Query.newEntityQueryBuilder()
            .setKind("Candidate")
            .build();
    QueryResults<Entity> queryResult = datastore.run(candidateQuery);
    assertThat(queryResult.hasNext()).isTrue();
    Entity candidateEntity = queryResult.next();
    assertThat(queryResult.hasNext()).isFalse();
    assertThat(candidateEntity.getKey().getId()).isEqualTo(candidateId);
    assertThat(candidateEntity.getString("name")).isEqualTo(candidate.get("name").getAsString());
    assertThat(candidateEntity.getString("party"))
        .isEqualTo(candidate.get("party").getAsString() + " Party");
    assertThat(candidateEntity.getString("email")).isEqualTo(candidate.get("email").getAsString());
    assertThat(candidateEntity.getString("phone")).isEqualTo(candidate.get("phone").getAsString());
    assertThat(candidateEntity.getString("candidateUrl"))
        .isEqualTo(candidate.get("candidateUrl").getAsString());
    assertThat(candidateEntity.getString("photoUrl"))
        .isEqualTo(candidate.get("photoUrl").getAsString());
    assertThat(candidateEntity.getString("twitter"))
        .isEqualTo(((JsonObject) candidate.getAsJsonArray("channels").get(0)).get("id").getAsString());
    assertThat(((Timestamp) electionEntity.getValue("lastModified").get()).compareTo(past) >= 0)
        .isTrue();
  }

  @Test
  public void compileInfo_checkEntireInfoCompilationProcess()
      throws IOException {
    // Execute the entire information compilation process. We don't test WebCrawler (for compiling
    // news articles) as that is tested in WebCrawlerTest. A Datastore emulator is used to simulate
    // Datastore operations, as opposed to Mockito mocks.
    Timestamp past = Timestamp.now();
    JsonObject electionJsonCopy = electionJson.deepCopy();
    JsonObject election =
        ((JsonObject) electionJsonCopy.getAsJsonArray("elections").get(0));
    String ocdDivisionId = DIVISION + "/state:" + STATE.toLowerCase();
    election.addProperty("ocdDivisionId", ocdDivisionId);
    String representativesQuery =
          String.format("%s/%s?key=%s", REPRENTATIVE_QUERY_URL_WITHOUT_KEY,
              URLEncoder.encode(ocdDivisionId), Config.CIVIC_INFO_API_KEY);
    InfoCompiler infoCompiler = new InfoCompiler(this.datastore);
    InfoCompiler infoCompilerSpy = spy(infoCompiler);
    // Set expiration time to a safely large value that will not cause new data to be cleared.
    infoCompilerSpy.DATA_EXPIRATION_SECONDS = 60 * 60 * 12;
    infoCompilerSpy.addresses = Arrays.asList(ADDRESS);
    infoCompilerSpy.webCrawler = mock(WebCrawler.class);
    doReturn(electionJsonCopy).when(infoCompilerSpy).queryCivicInformation(eq(ELECTION_QUERY_URL));
    doReturn(representativesJson).when(infoCompilerSpy).queryCivicInformation(eq(representativesQuery));
    JsonArray contests = new JsonArray();
    contests.add(singleContestJson);
    JsonObject contestsResponse = new JsonObject();
    contestsResponse.add("contests", contests);
    doReturn(contestsResponse).when(infoCompilerSpy).queryCivicInformation(eq(CONTEST_QUERY_URL));
    JsonObject candidate = (JsonObject) singleContestJson.getAsJsonArray("candidates").get(0);
    Long candidateId = new Long(candidate.get("name").getAsString().hashCode()
                                + candidate.get("party").getAsString().hashCode()
                                + election.get("name").getAsString().hashCode());
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date = new Date(
        Integer.parseInt(yearMonthDay[0]) - 1900,
        Integer.parseInt(yearMonthDay[1]) - 1,
        Integer.parseInt(yearMonthDay[2]),
        4,
        0);

    infoCompilerSpy.compileInfo();

    // Check election data.
    Query<Entity> electionQuery =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .build();
    QueryResults<Entity> queryResult = datastore.run(electionQuery);
    assertThat(queryResult.hasNext()).isTrue();
    Entity electionEntity = queryResult.next();
    assertThat(queryResult.hasNext()).isFalse();
    assertThat(electionEntity.getKey().getName()).isEqualTo(election.get("name").getAsString());
    assertThat(electionEntity.getString("queryId")).isEqualTo(election.get("id").getAsString());
    assertThat(electionEntity.getTimestamp("date").toDate()).isEqualTo(date);
    assertThat(electionEntity.getString("state")).isEqualTo(STATE);
    assertThat(((Timestamp) electionEntity.getValue("lastModified").get()).compareTo(past) >= 0)
        .isTrue();
    List<Value<String>> candidatePositions =
        new ArrayList<>(electionEntity.getList("candidatePositions"));
    assertThat(candidatePositions).hasSize(1);
    assertThat(candidatePositions)
        .containsExactly(StringValue.newBuilder(
                         singleContestJson.get("office").getAsString()).build());
    List<Value<String>> candidateIds =
        new ArrayList<>(electionEntity.getList("candidateIds"));
    assertThat(candidateIds).hasSize(1);
    assertThat(candidateIds)
        .containsExactly(StringValue.newBuilder(candidateId.toString()).build());
    List<Value<Boolean>> candidateIncumbency =
        new ArrayList<>(electionEntity.getList("candidateIncumbency"));
    assertThat(candidateIncumbency).hasSize(1);
    assertThat(candidateIncumbency)
        .containsExactly(BooleanValue.newBuilder(PLACEHOLDER_INCUMBENCY).build());
    // Check candidate data.
    Query<Entity> candidateQuery =
        Query.newEntityQueryBuilder()
            .setKind("Candidate")
            .build();
    queryResult = datastore.run(candidateQuery);
    assertThat(queryResult.hasNext()).isTrue();
    Entity candidateEntity = queryResult.next();
    assertThat(queryResult.hasNext()).isFalse();
    assertThat(candidateEntity.getKey().getId()).isEqualTo(candidateId);
    assertThat(candidateEntity.getString("name")).isEqualTo(candidate.get("name").getAsString());
    assertThat(candidateEntity.getString("party"))
        .isEqualTo(candidate.get("party").getAsString() + " Party");
    assertThat(candidateEntity.getString("email")).isEqualTo(candidate.get("email").getAsString());
    assertThat(candidateEntity.getString("phone")).isEqualTo(candidate.get("phone").getAsString());
    assertThat(candidateEntity.getString("candidateUrl"))
        .isEqualTo(candidate.get("candidateUrl").getAsString());
    assertThat(candidateEntity.getString("photoUrl"))
        .isEqualTo(candidate.get("photoUrl").getAsString());
    assertThat(candidateEntity.getString("twitter"))
        .isEqualTo(((JsonObject) candidate.getAsJsonArray("channels").get(0)).get("id").getAsString());
    assertThat(((Timestamp) electionEntity.getValue("lastModified").get()).compareTo(past) >= 0)
        .isTrue();
  }

  @Test
  public void compileInfo_discardTestElection2000()
      throws IOException {
    // Execute the entire information compilation process but for information of election of query
    // ID 2000, which is the test election of the Civic Information API and should be discarded.
    // We don't test WebCrawler (for compiling news articles) as that is tested in WebCrawlerTest.
    // A Datastore emulator is used to simulate Datastore operations, as opposed to Mockito mocks.
    JsonObject electionJsonCopy = electionJson.deepCopy();
    JsonObject election =
        ((JsonObject) electionJsonCopy.getAsJsonArray("elections").get(0));
    String ocdDivisionId = DIVISION + "/state:" + STATE.toLowerCase();
    election.addProperty("ocdDivisionId", ocdDivisionId);
    String representativesQuery =
          String.format("%s/%s?key=%s", REPRENTATIVE_QUERY_URL_WITHOUT_KEY,
              URLEncoder.encode(ocdDivisionId), Config.CIVIC_INFO_API_KEY);
    election.addProperty("id", InfoCompiler.TEST_VIP_ELECTION_QUERY_ID);
    InfoCompiler infoCompiler = new InfoCompiler(this.datastore);
    InfoCompiler infoCompilerSpy = spy(infoCompiler);
    infoCompilerSpy.addresses = Arrays.asList(ADDRESS);
    infoCompilerSpy.webCrawler = mock(WebCrawler.class);
    doReturn(electionJsonCopy).when(infoCompilerSpy).queryCivicInformation(eq(ELECTION_QUERY_URL));
    doReturn(electionJsonCopy).when(infoCompilerSpy).queryCivicInformation(eq(ELECTION_QUERY_URL));
    JsonArray contests = new JsonArray();
    contests.add(singleContestJson);
    JsonObject contestsResponse = new JsonObject();
    contestsResponse.add("contests", contests);
    doReturn(contestsResponse).when(infoCompilerSpy).queryCivicInformation(eq(CONTEST_QUERY_URL));
    JsonObject candidate = (JsonObject) singleContestJson.getAsJsonArray("candidates").get(0);
    Long candidateId = new Long(candidate.get("name").getAsString().hashCode()
                                + candidate.get("party").getAsString().hashCode()
                                + election.get("name").getAsString().hashCode());
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date = new Date(
        Integer.parseInt(yearMonthDay[0]) - 1900,
        Integer.parseInt(yearMonthDay[1]) - 1,
        Integer.parseInt(yearMonthDay[2]),
        4,
        0);

    infoCompilerSpy.compileInfo();

    // Check election data.
    Query<Entity> electionQuery =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .build();
    QueryResults<Entity> queryResult = datastore.run(electionQuery);
    assertThat(queryResult.hasNext()).isFalse();
    // Check candidate data.
    Query<Entity> candidateQuery =
        Query.newEntityQueryBuilder()
            .setKind("Candidate")
            .build();
    queryResult = datastore.run(candidateQuery);
    assertThat(queryResult.hasNext()).isFalse();
  }

  @Test
  public void compileInfo_infoExpires()
      throws IOException {
    // Execute the entire information compilation process and set {@code DATA_EXPIRATION_SECONDS}
    // such that all newly added would be considered outdated immediately and thus cleared.
    JsonObject electionJsonCopy = electionJson.deepCopy();
    JsonObject election =
        ((JsonObject) electionJsonCopy.getAsJsonArray("elections").get(0));
    String ocdDivisionId = DIVISION + "/state:" + STATE.toLowerCase();
    election.addProperty("ocdDivisionId", ocdDivisionId);
    String representativesQuery =
          String.format("%s/%s?key=%s", REPRENTATIVE_QUERY_URL_WITHOUT_KEY,
              URLEncoder.encode(ocdDivisionId), Config.CIVIC_INFO_API_KEY);
    InfoCompiler infoCompiler = new InfoCompiler(this.datastore);
    InfoCompiler infoCompilerSpy = spy(infoCompiler);
    // Any new data will be seen as outdated and thus cleared.
    infoCompilerSpy.DATA_EXPIRATION_SECONDS = -1;
    infoCompilerSpy.addresses = Arrays.asList(ADDRESS);
    infoCompilerSpy.webCrawler = mock(WebCrawler.class);
    doReturn(electionJsonCopy).when(infoCompilerSpy).queryCivicInformation(eq(ELECTION_QUERY_URL));
    doReturn(electionJsonCopy).when(infoCompilerSpy).queryCivicInformation(eq(ELECTION_QUERY_URL));
    JsonArray contests = new JsonArray();
    contests.add(singleContestJson);
    JsonObject contestsResponse = new JsonObject();
    contestsResponse.add("contests", contests);
    doReturn(contestsResponse).when(infoCompilerSpy).queryCivicInformation(eq(CONTEST_QUERY_URL));
    JsonObject candidate = (JsonObject) singleContestJson.getAsJsonArray("candidates").get(0);
    Long candidateId = new Long(candidate.get("name").getAsString().hashCode()
                                + candidate.get("party").getAsString().hashCode()
                                + election.get("name").getAsString().hashCode());
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date = new Date(
        Integer.parseInt(yearMonthDay[0]) - 1900,
        Integer.parseInt(yearMonthDay[1]) - 1,
        Integer.parseInt(yearMonthDay[2]),
        4,
        0);

    infoCompilerSpy.compileInfo();

    // Check election data.
    Query<Entity> electionQuery =
        Query.newEntityQueryBuilder()
            .setKind("Election")
            .build();
    QueryResults<Entity> queryResult = datastore.run(electionQuery);
    assertThat(queryResult.hasNext()).isFalse();
    // Check candidate data.
    Query<Entity> candidateQuery =
        Query.newEntityQueryBuilder()
            .setKind("Candidate")
            .build();
    queryResult = datastore.run(candidateQuery);
    assertThat(queryResult.hasNext()).isFalse();
  }

  @AfterClass
  public static void cleanup() throws InterruptedException, IOException, TimeoutException {
    datastoreHelper.stop();
  }
}
