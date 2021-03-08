package Client;

public interface IClient {
    int hashCode();
    boolean equals(Object obj);
    String toString();
    void sendMessage(String message);
    void sendMessage(String message, ICallback callback);
}
