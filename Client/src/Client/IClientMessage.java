package Client;

public interface IClientMessage {
    String toString();
    IClient getSender();
    String getMessage();
    boolean hasCallback();
    void reply(String message);
}
