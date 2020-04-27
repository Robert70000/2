package org.owntracks.android.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.owntracks.android.support.interfaces.IncomingMessageProcessor;
import org.owntracks.android.support.interfaces.OutgoingMessageProcessor;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "_type")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MessageCmd extends MessageBase{
    static final String TYPE = "cmd";
    private static final String BASETOPIC_SUFFIX = "/cmd";
    private String action;
    public static final String ACTION_REPORT_LOCATION = "reportLocation";
    public static final String ACTION_SET_WAYPOINTS = "setWaypoints";
    public static final String ACTION_SET_CONFIGURATION = "setConfiguration";
    public static final String ACTION_RESTART = "restart";
    public static final String ACTION_RECONNECT = "reconnect";
    public static final String ACTION_WAYPOINTS = "waypoints";

    private MessageWaypoints waypoints;
    private final Map<String,Object> map = new HashMap<>();
    private MessageConfiguration configuration;

    @JsonAnyGetter
    public Map<String,Object> any() {
        return map;
    }

    @JsonAnySetter
    public void set(String key, Object value) {
        if(value instanceof String && ((String) value).isEmpty())
            return;

        map.put(key, value);
    }

    @JsonIgnore
    @Nullable
    public Object get(String key) {
        return map.get(key);
    }

    @JsonIgnore
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }


    @Override
    @NonNull
    public String getBaseTopicSuffix() {  return BASETOPIC_SUFFIX; }

    @Nullable
    public String getAction() {
        return action;
    }

    public void setWaypoints(MessageWaypoints m) {
        this.waypoints = m;
    }

    public void setConfiguration(MessageConfiguration m) {
        this.configuration = m;
    }

    @Nullable
    public MessageWaypoints getWaypoints() {
        return waypoints;
    }

    @Nullable
    public MessageConfiguration getConfiguration() {
        return configuration;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public void processIncomingMessage(IncomingMessageProcessor handler) {
        handler.processIncomingMessage(this);
    }

    @Override
    public void processOutgoingMessage(OutgoingMessageProcessor handler) {
        handler.processOutgoingMessage(this);
    }

    @Override
    public boolean isValidMessage() {
        return super.isValidMessage() && (action != null);
    }

    @Override
    @JsonIgnore
    public void setTopic(String topic) {
        // Full topic is needed instead of the normalized base topic to verify if the message arrived on the correct topic
        this._topic = topic;
    }


}
