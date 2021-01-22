package Client;

import java.net.InetAddress;
import java.util.*;

class MainTest2 {

    static TCPClient client;

    static boolean flag = false;

    static final Set<Client> clients = new HashSet<>();

    static IServerMessageListener listener = message -> {
//        String[] args = message.split(":", 3);
//        System.out.println("Received message from " + from + ": " + message);
        if (message.getMessage().equals("Emitting")) {
            synchronized (clients) {
                clients.add(message.getSender());
            }
        }
    };

    public static void main(String[] args) throws Exception {
        client = new TCPClient(InetAddress.getLocalHost(), 54004);

//        client = new TCPClient(InetAddress.getByName("3.20.70.6"), 54003);

        client.addMessageListener(listener);

        client.start();

        ICallback fancyCallback = ICallback.make(Integer.MAX_VALUE,
                res -> System.out.println("Received response: " + res),
                () -> System.err.println("Did not receive response in time"));

        Scanner scanner = new Scanner(System.in);

        String type;
        String to;
        String input;
        while (true) {
            System.out.println("Enter message to send");
            to = scanner.nextLine();
            Optional<Client> address;
            synchronized (clients) {
                address = clients.stream().min(Comparator.comparingDouble(x ->Math.random()));
            }
            if (!address.isPresent()) {
                System.err.println("Address not present");
            }
            address.ifPresent(a -> {
                System.out.println("sending");
                long start = System.currentTimeMillis();
                client.sendMessage(a, "message", new ICallback() {
                    @Override
                    public void response(Message response) {
                        long time = System.currentTimeMillis() - start;
                        System.out.println("Time taken: " + time);
                    }

                    @Override
                    public int getTimeout() {
                        return Integer.MAX_VALUE;
                    }
                });
            });
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
