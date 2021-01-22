package Client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;

public class LoadTest2 {

    private static volatile boolean run = false;

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        for (int i = 0; i < 100; i++) {

            IClient client = new TCPClient(InetAddress.getLocalHost(), 54004);

//            IClient client = new TCPClient(InetAddress.getByName("3.20.70.6"), 54005);

            ICallback fancyCallback = ICallback.make(7,
                    res -> System.out.println("Received response: " + res),
                    () -> System.err.println("Did not receive response in time"));

            IServerMessageListener listener = message -> {
                System.out.println("Received message: " + message + " from " + message.getSender());
                if (Arrays.asList("Emitting", "Responding").contains(message.getMessage()))
                    message.getSender().sendMessage("Responding");
                else
                    message.reply("responding");
            };

            client.start();

            client.addMessageListener(listener);
            System.out.println("Client # " + i);
            new Thread(() -> {
                while(!run) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {

                    }
                }
//                while (true) {
//                    try {
//                        Thread.sleep(30000);
//                    } catch (InterruptedException e) {
//
//                    }
                    client.emitMessage("Emitting");
//                }
            }).start();
        }


        Scanner scanner = new Scanner(System.in);
        System.out.println("Press enter to continue");
        scanner.nextLine();
        run = true;

        while(true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
        }
    }

}
