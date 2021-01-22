package Client;

public class Message {

    private final Client sender;

    private final String message;

    private String responseId = null;

    Message(Client sender, String message) {
        this.sender = sender;
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }

    public Client getSender() {
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
        this.responseId = responseId;
    }

    String getResponseId() {
        return responseId;
    }

}
