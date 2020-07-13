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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

/**
 * A tester for the location-based information compiler.
 */
@RunWith(JUnit4.class)
public final class InfoCompilerTest {
  private final static String CIVIC_INFO_API_KEY = Config.CIVIC_INFO_API_KEY;
  private final static String ELECTION_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/elections?key=%s",
                    CIVIC_INFO_API_KEY);
  // @see Sample JSON structure:
  //     https://developers.google.com/civic-information/docs/using_api#electionquery-example.
  private final static String RESPONSE =
      "{" +
      " \"kind\": \"civicinfo#electionsqueryresponse\"," +
      " \"elections\": [" +
      "  {" +
      "   \"id\": \"2000\"," +
      "   \"name\": \"VIP Test Election\"," +
      "   \"electionDay\": \"2013-06-06\"" +
      "  }" +
      " ]" +
      "}";
  private JsonObject JSON;
  private InfoCompiler infoCompiler;

  @Before
  public void initialize() {
    infoCompiler = new InfoCompiler();
    JSON = new JsonObject();
    JSON.addProperty("kind", "civicinfo#electionsqueryresponse");
    JsonArray elections = new JsonArray(1);
    JsonObject election = new JsonObject();
    election.addProperty("id", "2000");
    election.addProperty("name", "VIP Test Election");
    election.addProperty("electionDay", "2013-06-06");
    elections.add(election);
    JSON.add("elections", elections);
  }

  @Test
  public void queryCivicInformation_succeedWithMockResponse() throws Exception {
    // Query the Civic Information API with a mock HTTP client + mock callback function of type
    // {@code ResponseHandler<String>} that converts the {@code HttpResponse} response to
    // {@code RESPONSE}, and subsequently convert {@code RESPONSE} to {@code json}.
    // Since String {@code RESPONSE} and JsonObject {@code JSON} match in content, {@code json}
    // should be exactly the same as {@code JSON}.
    CloseableHttpClient httpclient = mock(CloseableHttpClient.class);
    ArgumentCaptor<ResponseHandler<String>> argumentCaptor =
        ArgumentCaptor.forClass(ResponseHandler.class);
    when(httpclient.execute(anyObject(), argumentCaptor.capture())).thenReturn(RESPONSE);
    JsonObject json = infoCompiler.queryCivicInformation(ELECTION_QUERY_URL, httpclient);
    Assert.assertEquals(json, JSON);
  }

  // @TODO [Currently there are not good ways to mock {@code Datastore} (1) objects or (2)
  // operations, as indicated in {@code WebCrawlerTest}'s {@code storeInDatabase_*()} tests.
  // Might explore more advanced ways of mocking/spying to unit-test {@code Datastore} related
  // steps in future development.]

  @Test
  public void queryAndStoreBaseElectionInfo() {
    // Execute the entire process of querying and storing base election information. Should execute
    // without exceptions and the database should be populated correctly. This confirms the validity
    // of the query URLs and HTTP requests to the Civic Information API.
    // This is an integrated test.
    infoCompiler.queryAndStoreBaseElectionInfo();
  }

  @Test
  public void queryAndStoreElectionContestInfo() {
    // Execute the entire process of querying and storing election/position/candidate information.
    // This step is intended to run right after {@code queryAndStoreBaseElectionInfo()}.
    // Should execute without exceptions and the database should be populated correctly. This
    // confirms the validity of the query URLs and HTTP requests to the Civic Information API.
    // This is an integrated test.
    infoCompiler.queryAndStoreElectionContestInfo();
  }

  @Test
  public void queryAndStoreLocationBasedInfo() {
    // Execute the entire process of querying and storing location-based information. Should
    // execute without exceptions and the database should be populated correctly. This confirms the
    // validity of the query URLs and HTTP requests to the Civic Information API.
    // This is an integrated test.
    infoCompiler.queryAndStoreBaseElectionInfo();
    infoCompiler.queryAndStoreElectionContestInfo();
  }
}
