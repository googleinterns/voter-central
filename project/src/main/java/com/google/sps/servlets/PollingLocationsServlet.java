package com.google.sps.servlets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
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
import java.net.URLEncoder;

@WebServlet("/pollingLocation")
public class PollingLocationsServlet extends HttpServlet {
  private final static String VOTER_INFO_QUERY_URL =
    String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                  Config.CIVIC_INFO_API_KEY);
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String userAddress = request.getParameter("address");
    String encodedAddress = URLEncoder.encode(userAddress);
    String queryUrl = String.format("%s&address=%s", VOTER_INFO_QUERY_URL, encodedAddress);
    JsonObject voterInfoJson = queryCivicInformation(queryUrl);
    JsonArray pollingLocationsArray;
    //if (voterInfoJson.has("pollingLocations")) {
    pollingLocationsArray = voterInfoJson.getAsJsonArray("pollingLocations");
    //}
    JsonObject pollingLocationAddress = pollingLocationsArray.get(0).getAsJsonObject();

    Gson gson = new Gson();
    String pollingLocation = gson.toJson(pollingLocationAddress);

    // Send data in JSON format as the servlet response for the polling location page.
    response.setContentType("application/json;");
    response.getWriter().println(pollingLocation);
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
}
