private static InfoCompiler infoCompiler = new InfoCompiler();
private final static String VOTER_INFO_QUERY_URL =
    String.format("https://www.googleapis.com/civicinfo/v2/voterinfo?key=%s",
                  Config.CIVIC_INFO_API_KEY);
@WebServlet("/pollingLocation")
public class PollingLocationServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    
    String address = request.getParameter("Address");
    String queryUrl = String.format("%s&address=%s", VOTER_INFO_QUERY_URL, address);
    JsonArray pollingLocationData = infoCompiler.queryCivicInformation(queryUrl).getAsJsonArray("pollingLocations");
    Gson gson = new Gson();
    String pollingLocation = gson.toJson(pollingLocationData);

    // Send data in JSON format as the servlet response for the polling location page.
    response.setContentType("application/json;");
    response.getWriter().println(pollingLocation);
  }

