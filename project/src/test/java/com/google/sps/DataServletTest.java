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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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
  private static final String IRRELEVANT_ADDRESS_ELECTION_RESPONSE =
      "{" +
      "  state: [" +
      "    {" +
      "      sources: [" +
      "        {" +
      "          name: \"\"" +
      "        }" +
      "      ]" +
      "    }" +
      "  ]" +
      "}";
  private static final DataServlet dataServlet = new DataServlet();

  @Test
  public void requestHttpAndBuildJsonResponseFromCivicInformation_returnResponseStringAsIs()
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
        dataServlet.requestHttpAndBuildJsonResponseFromCivicInformation(httpClient, httpGet);
    assertThat(responseBody).isEqualTo(RELEVANT_ADDRESS_ELECTION_RESPONSE);
  }

  @Test
  public void parseResponseForRelevancy_relevantElection() {
    // Parse JSON-formatted String {@code RELEVANT_ADDRESS_ELECTION_RESPONSE} and see that it
    // indicates the election is relevant to the address according to the Civic Information
    // API response structure.
    assertThat(dataServlet.parseResponseForRelevancy(RELEVANT_ADDRESS_ELECTION_RESPONSE))
        .isTrue();
  }

  @Test
  public void parseResponseForRelevancy_irrelevantElection() {
    // Parse JSON-formatted String {@code IRRELEVANT_ADDRESS_ELECTION_RESPONSE} and see that it
    // indicates the election is irrelevant to the address according to the Civic Information
    // API response structure.
    assertThat(dataServlet.parseResponseForRelevancy(IRRELEVANT_ADDRESS_ELECTION_RESPONSE))
        .isFalse();
  }
}
