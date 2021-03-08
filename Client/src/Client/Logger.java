package Client;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class Logger {

    private final Consumer<String> logMethod;

    public Logger() {
        logMethod = this::defaultLog;
    }

    public Logger(Consumer<String> logMethod) {
        this.logMethod = logMethod;
    }

    void log(String string) {
        logMethod.accept(String.format("[CLIENT]: %s", string));
    }

    void logSendMessage(JsonObject message) {
        String logMessage;
        switch (message.get("type").getAsString()) {
            case "CONFIG":
                return;
            case "EMIT":
                logMessage = String.format("Emitting \"%s\"", message.get("data").getAsString());
                break;
            default:
                logMessage = String.format("Sending \"%s\" to %s", message.get("data").getAsString(),
                        message.get("receiver").getAsString().substring(0, 8));
        }
        log(logMessage);
    }

    void logReceiveMessage(JsonObject message) {
        String type = message.get("type").getAsString().equals("DIRECT") ? "message" : "response";
        log(String.format("Received %s \"%s\" from %s", type, message.get("data").getAsString(),
                message.get("sender").getAsString().substring(0, 8)));
    }

    private void defaultLog(String string) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        System.out.printf("%s %s%n", dtf.format(now), string);
    }

}
