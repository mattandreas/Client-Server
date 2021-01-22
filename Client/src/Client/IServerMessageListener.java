package Client;

import java.util.Optional;

public interface IServerMessageListener {
    void onMessage(Message message);

    default Optional<String> getSession() {
        return Optional.empty();
    }
}
