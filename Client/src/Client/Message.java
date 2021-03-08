package Client;

import java.util.UUID;

class Message implements IClientMessage {

    private final Client sender;

    private final String message;

    private UUID responseId = null;

    Message(Client sender, String message) {
        this.sender = sender;
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }

    public IClient getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasCallback() {
        return responseId != null;
    }

    public void reply(String message) {
        if (responseId == null) throw new RuntimeException("No callback exists for message");
        sender.sendResponse(message, responseId);
    }

    void setResponseId(String responseId) {
        this.responseId = UUID.fromString(responseId);
    }

    UUID getResponseId() {
        return responseId;
    }

}
