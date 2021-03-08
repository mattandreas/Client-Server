package Client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.UUID;

public class JsonConstructor {

    JsonObject emit(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "EMIT");
        json.addProperty("data", message);
        return json;
    }

    JsonObject emit(String session, String message) {
        JsonObject json = emit(message);
        json.addProperty("session", session);
        return json;
    }

    JsonObject direct(IClient receiver, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "DIRECT");
        json.addProperty("receiver", ((Client) receiver).getClientId());
        json.addProperty("data", message);
        return json;
    }

    JsonObject direct(IClient receiver, String message, UUID id) {
        JsonObject json = direct(receiver, message);
        json.addProperty("response_id", id.toString());
        return json;
    }

    JsonObject response(String to, UUID responseId, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "RESPONSE");
        json.addProperty("receiver", to);
        json.addProperty("response_id", responseId.toString());
        json.addProperty("data", message);
        return json;
    }

    JsonObject sessionConfig(boolean join, String... sessions) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CONFIG");

        JsonObject sessionInfo = new JsonObject();
        sessionInfo.addProperty("direction", join ? "join" : "leave");

        JsonArray names = new JsonArray();
        Arrays.stream(sessions).forEach(names::add);

        sessionInfo.add("names", names);

        json.add("session", sessionInfo);

        return json;
    }

    JsonObject heartbeat() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CONFIG");
        json.addProperty("heartbeat", "ping");
        return json;
    }

    Message toMessage(IOClient server, JsonObject json) {
        Client sender = new Client(server, json.get("sender").getAsString());
        Message message = new Message(sender, json.get("data").getAsString());
        if (json.has("response_id")) message.setResponseId(json.get("response_id").getAsString());
        return message;
    }

}
