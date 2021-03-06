package Client;

import java.util.UUID;

class Client implements IClient {

    private final IOClient server;

    private final String clientId;

    Client(IOClient server, String clientId) {
        this.server = server;
        this.clientId = clientId;
    }

    @Override
    public int hashCode() {
        return clientId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Client && clientId.equals(((Client) obj).clientId);
    }

    @Override
    public String toString() {
        return clientId;
    }

    public void sendMessage(String message) {
        server.sendMessage(this, message);
    }

    public void sendMessage(String message, ICallback callback) {
        server.sendMessage(this, message, callback);
    }

    void sendResponse(String message, UUID responseId) {
        server.sendResponse(clientId, responseId, message);
    }

    String getClientId() {
        return clientId;
    }
}
