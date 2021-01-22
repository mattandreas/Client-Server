package Client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TCPClient implements IClient {

    private final Consumer<String> logger;

    private final InetAddress serverAddress;

    private final int serverPort;

    private volatile Socket socket;

    private final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();

    private volatile boolean connectionAlive = true;

    private volatile boolean shouldRun = true;

    private volatile Thread runner = null;

    private final Object listenerLock = new Object();

    private final Map<String, Integer> sessions = new HashMap<>();

    private final List<IServerMessageListener> listeners = new ArrayList<>();

    private final HashMap<String, InternalCallback> callbacks = new HashMap<>();

    public TCPClient(InetAddress serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.logger = this::defaultLog;
    }

    public TCPClient(InetAddress serverAddress, int serverPort, Consumer<String> logger) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.logger = logger;
    }

    public synchronized void start() {
        if (runner == null) {
            shouldRun = true;
            runner = new Thread(this::run);
            runner.start();
        }
    }

    public synchronized void stop() {
        shouldRun = false;
        try {
            runner.interrupt();
            runner.join();
        } catch (InterruptedException e) {
            log("Interrupted while joining run thread");
        }
        runner = null;
    }

    @Override
    public void emitMessage(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "EMIT");
        json.addProperty("data", message);
        messages.add(json);
    }

    @Override
    public void emitMessage(String session, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "EMIT");
        json.addProperty("session", session);
        json.addProperty("data", message);
        messages.add(json);
    }

    @Override
    public void addMessageListener(IServerMessageListener listener) {
        synchronized (listenerLock) {
            listener.getSession().ifPresent(s -> handleSession(s, true));
            listeners.add(listener);
        }
    }

    @Override
    public void removeMessageListener(IServerMessageListener listener) {
        synchronized (listenerLock) {
            listener.getSession().ifPresent(s -> handleSession(s, false));
            listeners.remove(listener);
        }
    }

    void sendMessage(Client to, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "DIRECT");
        json.addProperty("receiver", to.getClientId());
        json.addProperty("data", message);
        messages.add(json);
    }

    void sendMessage(Client to, String message, ICallback callback) {
        InternalCallback internalCallback = new InternalCallback(callback);
        synchronized (listenerLock) {
            callbacks.put(internalCallback.getResponseId(), internalCallback);
        }
        startTimeout(internalCallback);
        JsonObject json = new JsonObject();
        json.addProperty("type", "DIRECT");
        json.addProperty("receiver", to.getClientId());
        json.addProperty("response_id", internalCallback.getResponseId());
        json.addProperty("data", message);
        messages.add(json);
    }

    void sendResponse(String to, String responseId, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "RESPONSE");
        json.addProperty("receiver", to);
        json.addProperty("response_id", responseId);
        json.addProperty("data", message);
        messages.add(json);
    }

    private void handleSession(String session, boolean join) {
        if (join) {
            if (sessions.containsKey(session)) {
                sessions.put(session, sessions.get(session) + 1);
            } else {
                sessions.put(session, 1);
                messages.add(getSessionConfigMessage(true, session));
            }
        } else {
            int current = sessions.getOrDefault(session, 1) - 1;
            if (current < 1) {
                sessions.remove(session);
                messages.add(getSessionConfigMessage(false, session));
            } else {
                sessions.put(session, current);
            }
        }
    }

    private JsonObject getSessionConfigMessage(boolean join, String... sessions) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CONFIG");

        JsonObject sessionInfo = new JsonObject();
        sessionInfo.addProperty("direction", join ? "join" : "leave");

        JsonArray names = new JsonArray();
        Arrays.stream(sessions).forEach(names::add);

        sessionInfo.add("names", names);

        json.add("session", sessionInfo);

        return json;
    }

    private void send() {
        log("Send starting");
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            JsonObject message;
            while (connectionAlive && shouldRun) {
                if ((message = messages.poll(100, TimeUnit.MILLISECONDS)) != null) {
                    if (!message.get("type").getAsString().equals("CONFIG")) {
                        log("Sending \"" + message + "\"");
                    }
                    out.println(message.toString());// + '\n');
                }
            }
        } catch (IOException e) {
            log("Error sending to server, stopping");
        } catch (InterruptedException e) {
            log("Send interrupted, stopping");
        } finally {
            connectionAlive = false;
        }
        log("Send exiting");
    }

    private void receive() {
        log("Receive thread Starting");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            while (connectionAlive && shouldRun) {
//                System.out.println("Attempting to read data");
                String message = in.readLine();
                if (message != null) {
//                    System.out.printf("RAW: %s%n", message);
                    JsonObject json = JsonParser.parseString(message).getAsJsonObject();
//                    if (message.equals("HEARTBEAT")) {
                    if (json.has("heartbeat")) {
//                        log("Received heartbeat, sending back");
//                        messages.add("HEARTBEAT");
                        beginSendHeartbeat();
                    } else {
//                        log("Received message: " + message);
                        processMessage(json);
                    }
                } else {
                    log("Connection lost, stopping");
                    break;
                }
            }
        } catch (IOException e) {
            log("Stream broke, exiting");
        } finally {
            connectionAlive = false;
        }
        log("Receive thread exiting");
    }

    private void beginSendHeartbeat() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                logger.accept("Heartbeat thread interrupted");
            }
            JsonObject heartbeat = new JsonObject();
            heartbeat.addProperty("type", "CONFIG");
            heartbeat.addProperty("heartbeat", "ping");
            messages.add(heartbeat);
        });
        try {
            thread.start();
        } catch (OutOfMemoryError e) {
            // Most likely exception from shutting down 1k clients at once during testing
        }
    }

    // Message received from another client
    private void processMessage(JsonObject json) {
        Client sender = new Client(this, json.get("sender").getAsString());
        Message message = new Message(sender, json.get("data").getAsString());
        if (json.has("response_id")) message.setResponseId(json.get("response_id").getAsString());
        synchronized (listenerLock) {
            if (json.get("type").getAsString().equals("RESPONSE")) {
                checkCallbacks(message);
            } else {
                listeners.forEach(l -> l.onMessage(message));
            }
        }
    }

    private void startTimeout(InternalCallback callback) {
        new Thread(() -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(callback.getCallback().getTimeout()));
            } catch (InterruptedException e) {
                log("Unable to sleep for callback timeout, removing now");
            }
            synchronized (listenerLock) {
                if (callbacks.containsKey(callback.getResponseId())) {
                    callback.getCallback().error();
                    callbacks.remove(callback.getResponseId());
                }
            }
        }).start();
    }

    private void checkCallbacks(Message message) {
        if (!callbacks.containsKey(message.getResponseId())) {
            log("Received a callback response, but no associated callback exists");
            return;
        }
        callbacks.get(message.getResponseId()).getCallback().response(message);
        callbacks.remove(message.getResponseId());
    }

    private void run() {
        log("Run thread starting");
        while (shouldRun) {
            try (Socket socket = new Socket(serverAddress, serverPort)) {

                this.socket = socket;

                connectionAlive = true;

                log("Connected to Server: " + socket.getInetAddress());

                Thread receive = new Thread(this::receive);

                receive.start();

//                messages.add("HEARTBEAT");
                beginSendHeartbeat();

                messages.add(getSessionConfigMessage(true, sessions.keySet().toArray(new String[0])));

                send();

                receive.join();
            } catch (IOException e) {
                log("Unable to connect to server, trying again in 5 seconds");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    log("Interrupted while sleeping between connects");
                    break;
                }
            } catch (InterruptedException e) {
                log("interrupted while waiting for receive thread to join");
                break;
            } finally {
                // connection died, clear all pending messages
                messages.clear();
            }
        }
        log("Run thread exiting");
    }

    private void log(String string) {
        logger.accept("[CLIENT]: " + string);
    }

    private void defaultLog(String string) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        System.out.println(dtf.format(now) + " " + string);
    }

}