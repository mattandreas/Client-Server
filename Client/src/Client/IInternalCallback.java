package Client;

import java.util.UUID;

class InternalCallback {

    private final String responseId = UUID.randomUUID().toString();

    private final ICallback callback;

    InternalCallback(ICallback callback) {
        this.callback = callback;
    }

    String getResponseId() {
        return responseId;
    }

    ICallback getCallback() {
        return callback;
    }

}
