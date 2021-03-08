package Client;

import java.net.InetAddress;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

class MainTest {

    static IOClient client;

    static final AtomicInteger num = new AtomicInteger(0);

    static IServerMessageListener listener = message -> {
//        String[] args = message.split(":", 3);
        System.out.println("Received message from " + message.getSender() + ": " + message);
        num.incrementAndGet();
        if (message.hasCallback()) {
            message.reply("Replying to message");
        }
    };

    public static void main(String[] args) throws Exception {
        client = new IOClient(InetAddress.getLocalHost(), 54004);

//        client = new IOClient(InetAddress.getByName("3.20.70.6"), 54003);

        client.addMessageListener(listener);

        client.start();

        Scanner scanner = new Scanner(System.in);

        String input;
        while (true) {
            System.out.println("Enter message to emit, session to send to, or stop/start");
            input = scanner.nextLine();
            if (input.equals("start")) {
                System.out.println("Starting client");
                client.start();
                continue;
            } else if (input.equals("stop")) {
                System.out.println("Stopping client");
                client.stop();
                continue;
            }
            Optional<String> session = Optional.empty();

            try {
                session = Optional.of(String.valueOf(Integer.parseInt(input)));
                System.out.printf("Enter message to emit to session %s%n", session.get());
                input = scanner.nextLine();
            } catch (Exception ignored) {}

            if (session.isPresent()) {
                System.out.printf("sending to session %s", session.get());
                client.emitMessage(session.get(), input);
            } else {
                num.set(0);
                long startTime = System.currentTimeMillis();
                client.emitMessage(input);
                while (num.get() < 1000) {
                    if (System.currentTimeMillis() > startTime + 5000) {
                        System.out.println("Timed out: " + num.get());
                        break;
                    }
                }
                Thread.sleep(100);
                System.out.printf("Got all responses in %d milliseconds", System.currentTimeMillis() - startTime);
            }

        }

//        System.out.println("Ending process");
    }



    private static String cleanTextContent(String text)
    {
        // strips off all non-ASCII characters
        text = text.replaceAll("[^\\x00-\\x7F]", "");

        // erases all the ASCII control characters
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // removes non-printable characters from Unicode
        text = text.replaceAll("\\p{C}", "");

        return text.trim();
    }

}
