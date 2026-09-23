package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HubServiceApp {

    private static final String INGESTION_URL = "http://localhost:7050/hubs";

    public static void main(String[] args) throws Exception {
        List<HubRecord> hubs = fetchHubsFromIngestion();
        Map<String, HubRecord> byId = hubs.stream()
                .collect(Collectors.toMap(h -> h.hubId, h -> h));

        Javalin app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> ctx.json(hubs));

        app.get("/hubs/{hubId}", ctx -> {
            String id = ctx.pathParam("hubId");
            HubRecord hub = byId.get(id);
            if (hub == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "No hub with id " + id));
            } else {
                ctx.json(hub);
            }
        });
    }

    private static List<HubRecord> fetchHubsFromIngestion() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                "ingestion-service returned " + response.statusCode() + " — is it running on :7050?");
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response.body(), mapper.getTypeFactory()
                .constructCollectionType(List.class, HubRecord.class));
    }
}