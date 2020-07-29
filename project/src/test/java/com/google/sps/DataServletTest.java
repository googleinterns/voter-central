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

import static com.google.common.truth.Truth.*;
import static com.google.common.truth.Truth8.*;
import static org.mockito.Mockito.*;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.IOException;
import java.net.SocketException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/**
 * A tester for the data servlet.
 */
@RunWith(JUnit4.class)
public class DataServletTest {
  // JSON response can be referenced below: @see <a href=
  // "https://developers.google.com/civic-information/docs/using_api#voterinfoquery-response:">
  // response structure</a>.
  private static final String RELEVANT_ADDRESS_ELECTION_RESPONSE =
      "{" +
      "  contests: []," +
      "  state: [" +
      "    {" +
      "      sources: [" +
      "        {" +
      "          name: \"Voting Information Project\"" +
      "        }" +
      "      ]" +
      "    }" +
      "  ]" +
      "}";
  private static final String RELEVANT_NONSPECIFIC_ADDRESS_ELECTION_RESPONSE =
      "{" +
      "  state: [" +
      "    {" +
      "      sources: [" +
      "        {" +
      "          name: \"Voting Information Project\"" +
      "        }" +
      "      ]" +
      "    }" +
      "  ]" +
      "}";
  private static final String QUERY_ID = "1000";
  private static final String ADDRESS = "Sample address";
  private static final DataServlet dataServlet = new DataServlet();

  private static final LocalServiceTestHelper datastoreHelper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  @Before
  public void initialize() {
    datastoreHelper.setUp();
  }

  @Test
  public void requestHttpAndBuildCivicInfoResponse_returnResponseStringAsIs()
      throws IOException, SocketException {
    // Query the Civic Information API with a mock HTTP client + mock callback function of type
    // {@code ResponseHandler<String>} that converts any {@code HttpResponse} response to {@code
    // RELEVANT_ADDRESS_ELECTION_RESPONSE}. {@code httpGet} is irrelevant in this test. The
    // method should return the response as is without changing it.
    CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
    HttpGet httpGet = new HttpGet("");
    ArgumentCaptor<ResponseHandler<String>> argumentCaptor =
        ArgumentCaptor.forClass(ResponseHandler.class);
    when(httpClient.execute(anyObject(), argumentCaptor.capture()))
        .thenReturn(RELEVANT_ADDRESS_ELECTION_RESPONSE);
    String responseBody =
        dataServlet.requestHttpAndBuildCivicInfoResponse(httpClient, httpGet);
    httpClient.close();
    assertThat(responseBody).isEqualTo(RELEVANT_ADDRESS_ELECTION_RESPONSE);
  }

  @Test
  public void parseResponseForRelevancy_relevantElection() {
    // Parse JSON-formatted String {@code RELEVANT_ADDRESS_ELECTION_RESPONSE} and see that it
    // indicates the election is relevant to the address and the address is both specific and
    // counts as a residential address, according to the Civic Information API response structure.
    dataServlet.parseResponseForRelevancy(RELEVANT_ADDRESS_ELECTION_RESPONSE);
    assertThat(dataServlet.isAddressRelevantButNotSpecificOrResidential).isFalse();
  }

  @Test
  public void parseResponseForRelevancy_relevantNonspecificElection() {
    // Parse JSON-formatted String {@code RELEVANT_NONSPECIFIC_ADDRESS_ELECTION_RESPONSE} and see
    // that it,indicates the election is probably relevant to the address according to the Civic
    // Information API response structure, but the address is nonspecific.
    dataServlet.isAddressRelevantButNotSpecificOrResidential = false;
    dataServlet.parseResponseForRelevancy(RELEVANT_NONSPECIFIC_ADDRESS_ELECTION_RESPONSE);
    assertThat(dataServlet.isAddressRelevantButNotSpecificOrResidential).isTrue();
  }

  @Test
  public void isRelevantElection_irrelevantElection() throws IOException {
    // See that {@code isRelevantElection()} returns false for irrelevant elections, which will
    // cause {@code queryCivicInformation()} to throw an exception. Use App Engine development
    // tools for testing Datastore locally in memory.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    DataServlet dataServletMock = mock(DataServlet.class);
    boolean listAllElections = false;
    Entity election = new Entity("Election");
    election.setProperty("queryId", (Object) QUERY_ID);
    when(dataServletMock.queryCivicInformation(anyString()))
        .thenThrow(ClientProtocolException.class);
    when(dataServletMock.isRelevantElection(election, ADDRESS, listAllElections))
        .thenCallRealMethod();
    assertThat(dataServletMock.isRelevantElection(election, ADDRESS, listAllElections)).isFalse();
  }

  @Test
  public void isRelevantElection_listAllElections() throws IOException {
    // See that {@code isRelevantElection()} returns true when {@code listAllElections} is set to
    // true, even for irrelevant elections, which will cause {@code queryCivicInformation()} to
    // throw an exception. Use App Engine development tools for testing Datastore locally in
    // memory.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    DataServlet dataServletMock = mock(DataServlet.class);
    boolean listAllElections = true;
    Entity election = new Entity("Election");
    election.setProperty("queryId", (Object) QUERY_ID);
    when(dataServletMock.queryCivicInformation(anyString()))
        .thenThrow(ClientProtocolException.class);
    when(dataServletMock.isRelevantElection(election, ADDRESS, listAllElections))
        .thenCallRealMethod();
    assertThat(dataServletMock.isRelevantElection(election, ADDRESS, listAllElections)).isTrue();
  }

  @After
  public void cleanup() {
    datastoreHelper.tearDown();
  }
}
