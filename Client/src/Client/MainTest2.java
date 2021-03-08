package Client;

import java.net.InetAddress;
import java.util.*;

class MainTest2 {

    static IOClient client;

    static boolean flag = false;

    static final Set<Client> clients = new HashSet<>();

    static IServerMessageListener listener = message -> {
//        String[] args = message.split(":", 3);
//        System.out.println("Received message from " + from + ": " + message);
        if (message.getMessage().equals("Emitting")) {
            synchronized (clients) {
                clients.add((Client) message.getSender());
            }
        }
    };

    public static void main(String[] args) throws Exception {
        client = new IOClient(InetAddress.getLocalHost(), 54004);

//        client = new IOClient(InetAddress.getByName("3.20.70.6"), 54003);

        client.addMessageListener(listener);

        client.start();

        ICallback fancyCallback = ICallback.make(Integer.MAX_VALUE,
                res -> System.out.println("Received response: " + res),
                () -> System.err.println("Did not receive response in time"));

        Scanner scanner = new Scanner(System.in);

        while (true) {
            if (client.isRunning()) {
                System.out.println("Press any key to send message to random client, or x to stop client");
                String str = scanner.nextLine();
                if (str.equals("x")) {
                    client.stop();
                    continue;
                }
                Optional<Client> address;
                synchronized (clients) {
                    address = clients.stream().min(Comparator.comparingDouble(x -> Math.random()));
                }
                if (!address.isPresent()) {
                    System.err.println("Address not present");
                }
                address.ifPresent(a -> {
                    System.out.println("sending");
                    long start = System.currentTimeMillis();
                    client.sendMessage(a, "message", new ICallback() {
                        @Override
                        public void response(IClientMessage response) {
                            long time = System.currentTimeMillis() - start;
                            System.out.println("Time taken: " + time);
                        }

                        @Override
                        public int getTimeout() {
                            return Integer.MAX_VALUE;
                        }
                    });
                });
            } else {
                System.out.println("Press any key to restart client");
                scanner.nextLine();
                client.start();
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
