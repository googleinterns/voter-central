import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    JsonArray pollingLocationsArray = infoCompiler.queryCivicInformation(queryUrl).getAsJsonArray("pollingLocations");
    JsonObject Address pollingLocationsArray.get(0).getAsJsonObject();
    pollingLocation
    Gson gson = new Gson();
    String pollingLocation = gson.toJson(Address);

    // Send data in JSON format as the servlet response for the polling location page.
    response.setContentType("application/json;");
    response.getWriter().println(pollingLocation);
  }

