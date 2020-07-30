private final static String VOTER_INFO_QUERY_URL =
      String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                    Config.CIVIC_INFO_API_KEY);

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
