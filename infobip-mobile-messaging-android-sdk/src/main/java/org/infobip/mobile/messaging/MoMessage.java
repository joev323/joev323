package org.infobip.mobile.messaging;

import org.infobip.mobile.messaging.api.support.http.serialization.JsonSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author sslavin
 * @since 21/07/16.
 */
public class MoMessage {

    String destination;
    String text;
    Map<String, Object> customPayload;

    protected String messageId;
    protected Status status;

    public MoMessage(String destination, String text, Map<String, Object> customPayload) {
        this.destination = destination;
        this.text = text;
        this.customPayload = customPayload;
        this.messageId = "";
        this.status = Status.UNKNOWN;
    }

    private MoMessage(String message) {
        JsonSerializer jsonSerializer = new JsonSerializer();
        MoMessage moMessage = jsonSerializer.deserialize(message, MoMessage.class);
        this.destination = moMessage.destination;
        this.text = moMessage.text;
        this.customPayload = moMessage.customPayload;
        this.messageId = moMessage.messageId;
        this.status = moMessage.status;
    }

    public static MoMessage[] createFrom(List<String> messages) {
        List<MoMessage> moMessages = new ArrayList<>();
        JsonSerializer jsonSerializer = new JsonSerializer();
        for (String message : messages) {
            moMessages.add(jsonSerializer.deserialize(message, MoMessage.class));
        }
        return moMessages.toArray(new MoMessage[moMessages.size()]);
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getCustomPayload() {
        return customPayload;
    }

    public void setCustomPayload(Map<String, Object> customPayload) {
        this.customPayload = customPayload;
    }

    public String getMessageId() {
        return messageId;
    }

    public Status getStatus() {
        return status;
    }

    public enum Status {
        SUCCESS,
        ERROR,
        UNKNOWN
    }
}