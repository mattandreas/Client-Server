package Client;

public interface IO {
    void start();

    void stop();

    boolean isRunning();

    void emitMessage(String message);

    void emitMessage(String session, String message);

    void addMessageListener(IServerMessageListener listener);

    void removeMessageListener(IServerMessageListener listener);

}
