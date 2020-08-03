package com/google/sps/servlets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.sps.infocompiler;

private static InfoCompiler infoCompiler = new InfoCompiler();
private final static String VOTER_INFO_QUERY_URL =
    String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                  Config.CIVIC_INFO_API_KEY);
@WebServlet("/pollingLocation")
public class PollingLocationServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    
    String userAddress = request.getParameter("address");
    String queryUrl = String.format("%s&address=%s", VOTER_INFO_QUERY_URL, userAddress);
    JsonArray voterInfoArray = infoCompiler.queryCivicInformation(queryUrl);
    if (voterInfoArray.has("pollingLocations")) {
      JsonArray pollingLocation voterInfoArray.getAsJsonArray("pollingLocations");
    }
    JsonObject pollingLocationAddress pollingLocationsArray.get(0).getAsJsonObject();

    Gson gson = new Gson();
    String pollingLocation = gson.toJson(pollingLocationAddress);

    // Send data in JSON format as the servlet response for the polling location page.
    response.setContentType("application/json;");
    response.getWriter().println(pollingLocation);
  }

