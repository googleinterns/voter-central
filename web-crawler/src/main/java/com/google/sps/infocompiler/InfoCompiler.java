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

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.TimestampValue;
import com.google.cloud.Timestamp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Date;
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
  private final static String CIVIC_INFO_API_KEY = "AIzaSyDg8QhN9VUyKammq27hd46XlXtlTTpzBls";
  private final static String ELECTION_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/elections?key=%s",
                    CIVIC_INFO_API_KEY);

  /** 
   * Queries the ElectionQuery of the Civic Information API for a small subset of election
   * information, which will serve as the starting point for finding additional information,
   * and stores said found information in the database.
   * Information includes: name, date, and query ID of the election for the Civic Information API.
   *
   * @throws ClientProtocolException if the GET request to the Civic Information API fails.
   * @see <a href=
   *    "https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/" +
   *    "http/examples/client/ClientWithResponseHandler.java">Code reference</a>
   */
  public void queryAndStoreSubsetOfElectionInfo() {
    JsonObject elections;
    try {
      elections = queryCivicInformation(ELECTION_QUERY_URL);
    } catch (IOException e) {
      System.out.println(
          "[ERROR] Failed to query the Civic Information API for election names and IDs: " + e);
      System.exit(-1);
    }
    for (JsonElement election : elections.getAsJsonArray("elections")) {
      storeElectionInDatabase((JsonObject) election);
    }
  }

  /** 
   * Queries the Civic Information API and retrieves JSON response.
   *
   * @throws ClientProtocolException if the GET request to the Civic Information API fails.
   * @see <a href=
   *    "https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/" +
   *    "http/examples/client/ClientWithResponseHandler.java">Code reference</a>
   */
  private JsonObject queryCivicInformation(String queryUrl) throws IOException {
    CloseableHttpClient httpclient = HttpClients.createDefault();
    HttpGet httpGet = new HttpGet(queryUrl);
    ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
      @Override
      public String handleResponse(final HttpResponse response) throws IOException {
        int status = response.getStatusLine().getStatusCode();
        if (status >= 200 && status < 300) {
            HttpEntity entity = response.getEntity();
            return entity != null ? EntityUtils.toString(entity) : null;
        } else {
            httpclient.close();
            throw new ClientProtocolException("Unexpected response status: " + status);
        }
      }
    };
    String responseBody = httpclient.execute(httpGet, responseHandler);
    httpclient.close();
    JsonObject json = new JsonParser().parse(responseBody).getAsJsonObject();
    return json;
  }

  /**
   * Stores the name, date and query ID of {@code election} in to the database.
   * The original format of the election day is "YYYY-MM-DD", and the specific hour/minute/second
   * is irrelevant.
   */
  private void storeElectionInDatabase(JsonObject election) {
    Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    Key electionKey = datastore.newKeyFactory()
        .setKind("Election")
        .newKey(election.get("name").getAsString());
    String[] yearMonthDay = election.get("electionDay").getAsString().split("-");
    Date date = new Date(Integer.parseInt(yearMonthDay[0]) - 1900,
                         Integer.parseInt(yearMonthDay[1]) - 1,
                         Integer.parseInt(yearMonthDay[2]),
                         4, 0); // Convert from UTC to EDT with 4 hours.
    Entity electionEntity = Entity.newBuilder(electionKey)
        .set("queryId", election.get("id").getAsString())
        .set("date", TimestampValue.newBuilder(Timestamp.of(date)).build())
        .build();
    datastore.put(electionEntity);
  }

  // For testing purposes.
  public static void main(String[] args) {
    InfoCompiler myInfoCompiler = new InfoCompiler();
    myInfoCompiler.queryAndStoreSubsetOfElectionInfo();
    // Prevent {@code java.lang.IllegalThreadStateException}.
    System.exit(0);
  }
}
