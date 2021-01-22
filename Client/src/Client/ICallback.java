package Client;

import java.util.function.Consumer;

public interface ICallback {
    void response(Message response);
    default void error() {
        System.err.println("Callback timed out");
    }
    default int getTimeout() {
        return 10;
    }
    static ICallback make(int timeout, Consumer<Message> onResponse, Runnable onError) {
        return new ICallback() {
            @Override
            public void response(Message message) {
                onResponse.accept(message);
            }
            @Override
            public void error() {
                onError.run();
            }
            @Override
            public int getTimeout() {
                return timeout;
            }
        };
    }
}