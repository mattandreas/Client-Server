package Client;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface ICallback {
    void response(IClientMessage response);
    default void error() {
        System.err.println("Callback timed out");
    }
    default int getTimeout() {
        return 10;
    }
    default TimeUnit getTimeUnit() {
        return TimeUnit.SECONDS;
    }
    static ICallback make(int timeout, Consumer<IClientMessage> onResponse, Runnable onError) {
        return new ICallback() {
            @Override
            public void response(IClientMessage message) {
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