package Client;

public interface IClient {
    void start();

    void stop();

    void emitMessage(String message);

    void emitMessage(String session, String message);

    void addMessageListener(IServerMessageListener listener);

    void removeMessageListener(IServerMessageListener listener);

}
