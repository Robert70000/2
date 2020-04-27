package org.owntracks.android.data.repos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.owntracks.android.data.WaypointModel;
import org.owntracks.android.messages.MessageWaypoint;
import org.owntracks.android.support.Events;
import org.owntracks.android.support.MessageWaypointCollection;

import java.util.List;

import io.objectbox.android.ObjectBoxLiveData;
import io.objectbox.query.Query;


public abstract class WaypointsRepo {
    private EventBus eventBus;
    protected WaypointsRepo(EventBus eventBus) {
        this.eventBus = eventBus;
    }
    public abstract WaypointModel get(long tst);
    protected abstract List<WaypointModel> getAll();
    public abstract List<WaypointModel> getAllWithGeofences();
    public abstract ObjectBoxLiveData<WaypointModel> getAllLive();
    public abstract Query<WaypointModel> getAllQuery();

    public void insert(WaypointModel w) {
        insert_impl(w);
        eventBus.post(new Events.WaypointAdded(w));

    }

    public void update(WaypointModel w, boolean notify) {
        update_impl(w);
        if(notify) {
            eventBus.post(new Events.WaypointUpdated(w));
        }
    }

    public void delete(WaypointModel w) {
        delete_impl(w);
        eventBus.post(new Events.WaypointRemoved(w));
    }

    public void importFromMessage(@Nullable MessageWaypointCollection waypoints) {
        if(waypoints == null)
            return;

        for (MessageWaypoint m: waypoints) {
            // Delete existing waypoint if one with the same tst already exists
            WaypointModel exisiting = get(m.getTst());
            if(exisiting != null) {
                delete(exisiting);
            }
            insert(toDaoObject(m));
        }
    }

    @NonNull
    public MessageWaypointCollection exportToMessage() {
        MessageWaypointCollection messages = new MessageWaypointCollection();
        for(WaypointModel waypoint : getAll()) {
            messages.add(fromDaoObject(waypoint));
        }
        return messages;
    }

    private WaypointModel toDaoObject(@NonNull MessageWaypoint messageWaypoint) {
        return new WaypointModel(0, messageWaypoint.getTst(),messageWaypoint.getDesc(), messageWaypoint.getLat(), messageWaypoint.getLon(), messageWaypoint.getRad() != null ? messageWaypoint.getRad() : 0, 0, 0);
    }

    public MessageWaypoint fromDaoObject(@NonNull WaypointModel w) {
        MessageWaypoint message = new MessageWaypoint();
        message.setDesc(w.getDescription());
        message.setLat(w.getGeofenceLatitude());
        message.setLon(w.getGeofenceLongitude());
        message.setRad(w.getGeofenceRadius());
        message.setTst(w.getTst());
        return message;
    }

    protected abstract void insert_impl(WaypointModel w);
    protected abstract void update_impl(WaypointModel w);
    protected abstract void delete_impl(WaypointModel w);

}