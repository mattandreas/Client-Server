package Client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public class LoadTest {

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        for (int i = 0; i < 1000; i++) {
            int session = (i / 100) + 1;

            IO client = new IOClient(InetAddress.getLocalHost(), 54004);

            ICallback callback = res -> {
                System.out.printf("Received response \"%s\" from %s%n", res.getMessage(), res.getSender());
            };

            ICallback callbackWithTimeout = new ICallback() {
                @Override
                public void response(IClientMessage response) {
                    System.out.println("Received response: " + response);
                }

                @Override
                public void error() {
                    System.err.println("Did not receive response in time");
                }

                @Override
                public int getTimeout() {
                    return 1;
                }
            };

            ICallback fancyCallback = ICallback.make(7,
                    res -> System.out.printf("Received response \"%s\" from %s%n", res.getMessage(), res.getSender()),
                    () -> System.err.println("Did not receive response in time"));

            IServerMessageListener listener = message -> {
                System.out.println("Received message: " + message + " from " + message.getSender());
                message.getSender().sendMessage("Responding with callback", fancyCallback);
            };

            IServerMessageListener sessionListener = new IServerMessageListener() {
                @Override
                public void onMessage(IClientMessage message) {
                    System.out.printf("Received session %d message \"%s\" from %s%n",
                            session, message.getMessage(), message.getSender());
                }

                @Override
                public Optional<String> getSession() {
                    return Optional.of(String.valueOf(session));
                }
            };

            client.start();

            client.addMessageListener(listener);
            client.addMessageListener(sessionListener);
            System.out.println("Client # " + i);
//            Thread.sleep(50);
        }

        while(true) {

        }
    }
}
