package Client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IOClient implements IO {

    private final InetAddress serverAddress;

    private final int serverPort;

    private final Logger logger;

    private final JsonConstructor jsonConstructor = new JsonConstructor();

    private final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();

    private final Map<String, Integer> sessions = new HashMap<>();

    private final Set<IServerMessageListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<UUID, ICallback> callbacks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();

    private ScheduledExecutorService runner = null;

    public IOClient(InetAddress serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.logger = new Logger();
    }

    public IOClient(InetAddress serverAddress, int serverPort, Consumer<String> logMethod) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.logger = new Logger(logMethod);
    }

    @Override
    public synchronized boolean isRunning() {
        return runner != null;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) return;
        logger.log("IO service starting");
        runner = Executors.newSingleThreadScheduledExecutor();
        runner.submit(() -> run(runner));
    }

    @Override
    public synchronized void stop() {
        if (!isRunning()) return;
        logger.log("IO service stopping");
        runner.shutdownNow();
        runner = null;
    }

    @Override
    public void emitMessage(String message) {
        JsonObject json = jsonConstructor.emit(message);
        messages.add(json);
    }

    @Override
    public void emitMessage(String session, String message) {
        JsonObject json = jsonConstructor.emit(session, message);
        messages.add(json);
    }

    @Override
    public void addMessageListener(IServerMessageListener listener) {
        if (listeners.add(listener)) {
            listener.getSession().ifPresent(this::joinSession);
        }
    }

    @Override
    public void removeMessageListener(IServerMessageListener listener) {
        if (listeners.remove(listener)) {
            listener.getSession().ifPresent(this::leaveSession);
        }
    }

    void sendMessage(IClient to, String message) {
        JsonObject json = jsonConstructor.direct(to, message);
        messages.add(json);
    }

    void sendMessage(IClient to, String message, ICallback callback) {
        UUID id = UUID.randomUUID();
        callbacks.put(id, callback);
        timeoutExecutor.schedule(() -> checkTimeout(id), callback.getTimeout(), callback.getTimeUnit());
        JsonObject json = jsonConstructor.direct(to, message, id);
        messages.add(json);
    }

    void sendResponse(String to, UUID responseId, String message) {
        JsonObject json = jsonConstructor.response(to, responseId, message);
        messages.add(json);
    }

    private void joinSession(String session) {
        synchronized (sessions) {
            if (sessions.containsKey(session)) {
                sessions.put(session, sessions.get(session) + 1);
            } else {
                sessions.put(session, 1);
                JsonObject configMessage = jsonConstructor.sessionConfig(true, session);
                messages.add(configMessage);
            }
        }
    }

    private void leaveSession(String session) {
        synchronized (sessions) {
            int current = sessions.getOrDefault(session, 1) - 1;
            if (current < 1) {
                sessions.remove(session);
                JsonObject configMessage = jsonConstructor.sessionConfig(false, session);
                messages.add(configMessage);
            } else {
                sessions.put(session, current);
            }
        }
    }

    private void send(Socket socket) {
        logger.log("Send starting");
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            for (JsonObject message; (message = messages.take()) != null; ) {
                logger.logSendMessage(message);
                out.println(message.toString());
            }
        } catch (IOException e) {
            logger.log("Send stream broke");
        } catch (InterruptedException e) {
            logger.log("Send interrupted");
        }
    }

    private void receive(Socket socket) {
        logger.log("Receive starting");
        ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.schedule(this::sendHeartbeat, 1, TimeUnit.SECONDS);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            for (String message; (message = in.readLine()) != null; ) {
                JsonObject json = new JsonParser().parse(message).getAsJsonObject();
                if (json.has("heartbeat")) {
                    heartbeatScheduler.schedule(this::sendHeartbeat, 1, TimeUnit.SECONDS);
                } else {
                    logger.logReceiveMessage(json);
                    processMessage(json);
                }
            }
            logger.log("Receive connection lost");
        } catch (IOException e) {
            logger.log("Receive stream broke");
        } finally {
            heartbeatScheduler.shutdownNow();
        }
    }

    private void sendHeartbeat() {
//        log("Sending heartbeat");
        JsonObject heartbeat = jsonConstructor.heartbeat();
        messages.add(heartbeat);
    }

    // Message received from another client
    private void processMessage(JsonObject json) {
        Message message = jsonConstructor.toMessage(this, json);
        if (json.get("type").getAsString().equals("RESPONSE")) {
            checkCallbacks(message);
        } else {
            listeners.forEach(l -> l.onMessage(message));
        }
    }

    private void checkTimeout(UUID callbackId) {
        ICallback callback = callbacks.remove(callbackId);
        if (callback == null) return;
        callback.error();
    }

    private void checkCallbacks(Message message) {
        ICallback callback = callbacks.remove(message.getResponseId());
        if (callback != null) {
            callback.response(message);
        } else {
            logger.log("Received a callback response, but no associated callback exists");
        }
    }

    private void addSessionsConfig() {
        synchronized (sessions) {
            if (sessions.isEmpty()) return;
            messages.add(jsonConstructor.sessionConfig(true, sessions.keySet().toArray(new String[0])));
        }
    }

    private void run(ScheduledExecutorService runner) {
        try (Socket socket = new Socket(serverAddress, serverPort)) {
            logger.log(String.format("Connected to Server: %s", socket.getInetAddress()));
            addSessionsConfig();
            Executors.newFixedThreadPool(2)
                    .invokeAny(Stream.of((Runnable) () -> receive(socket), () -> send(socket))
                            .map(Executors::callable).collect(Collectors.toList()));
            logger.log("Connection died, restarting");
        } catch (IOException e) {
            logger.log("Unable to connect to server, trying again in 5 seconds");
        } catch (ExecutionException | InterruptedException e) {
            logger.log("Exiting IO service");
            return;
        } finally {
            // clear all pending messages
            messages.clear();
        }
        runner.schedule(() -> run(runner), 5, TimeUnit.SECONDS);
    }

}