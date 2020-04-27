package org.owntracks.android.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.owntracks.android.R;
import org.owntracks.android.data.WaypointModel;
import org.owntracks.android.data.repos.ContactsRepo;
import org.owntracks.android.data.repos.LocationRepo;
import org.owntracks.android.data.repos.WaypointsRepo;
import org.owntracks.android.messages.MessageLocation;
import org.owntracks.android.messages.MessageTransition;
import org.owntracks.android.model.FusedContact;
import org.owntracks.android.services.worker.Scheduler;
import org.owntracks.android.support.DateFormatter;
import org.owntracks.android.support.Events;
import org.owntracks.android.support.GeocodingProvider;
import org.owntracks.android.support.Preferences;
import org.owntracks.android.support.Runner;
import org.owntracks.android.support.ServiceBridge;
import org.owntracks.android.ui.map.MapActivity;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.android.DaggerService;
import timber.log.Timber;

public class BackgroundService extends DaggerService implements OnCompleteListener<Location>,Preferences.OnPreferenceChangedListener, ServiceBridge.ServiceBridgeInterface {
    private static final int INTENT_REQUEST_CODE_LOCATION = 1263;
    private static final int INTENT_REQUEST_CODE_GEOFENCE = 1264;
    private static final int INTENT_REQUEST_CODE_CLEAR_EVENTS = 1263;

    private static final int NOTIFICATION_ID_ONGOING = 1;
    private static final String NOTIFICATION_CHANNEL_ONGOING = "O";

    private static final int NOTIFICATION_ID_EVENT_GROUP = 2;
    private static final String NOTIFICATION_CHANNEL_EVENTS = "E";

    private static int notificationEventsID = 3;

    private final String NOTIFICATION_GROUP_EVENTS = "events";

    // NEW ACTIONS ALSO HAVE TO BE ADDED TO THE SERVICE INTENT FILTER
    private static final String INTENT_ACTION_CLEAR_NOTIFICATIONS = "org.owntracks.android.CLEAR_NOTIFICATIONS";
    private static final String INTENT_ACTION_SEND_LOCATION_USER = "org.owntracks.android.SEND_LOCATION_USER";
    private static final String INTENT_ACTION_SEND_EVENT_CIRCULAR = "org.owntracks.android.SEND_EVENT_CIRCULAR";
    private static final String INTENT_ACTION_REREQUEST_LOCATION_UPDATES = "org.owntracks.android.REREQUEST_LOCATION_UPDATES";
    private static final String INTENT_ACTION_CHANGE_MONITORING = "org.owntracks.android.CHANGE_MONITORING";

    private FusedLocationProviderClient mFusedLocationClient;
    private GeofencingClient mGeofencingClient;

    private LocationCallback locationCallback;
    private LocationCallback locationCallbackOnDemand;
    private MessageLocation lastLocationMessage;
    private MessageProcessor.EndpointState lastEndpointState = MessageProcessor.EndpointState.INITIAL;


    private NotificationCompat.Builder activeNotificationCompatBuilder;
    private NotificationCompat.Builder eventsNotificationCompatBuilder;
    private NotificationManager notificationManager;

    private NotificationManagerCompat notificationManagerCompat;

    private final LinkedList<Spannable> activeNotifications = new LinkedList<>();
    private int lastQueueLength = 0;
    private Notification stackNotification;

    @Inject
    Preferences preferences;

    @Inject
    EventBus eventBus;

    @Inject
    Scheduler scheduler;

    @Inject
    LocationProcessor locationProcessor;

    @Inject
    GeocodingProvider geocodingProvider;

    @Inject
    ContactsRepo contactsRepo;

    @Inject
    LocationRepo locationRepo;

    @Inject
    Runner runner;

    @Inject
    WaypointsRepo waypointsRepo;

    @Inject
    ServiceBridge serviceBridge;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceBridge.bind(this);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mGeofencingClient = LocationServices.getGeofencingClient(this);
        notificationManagerCompat = NotificationManagerCompat.from(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onLocationChanged(locationResult.getLastLocation(),MessageLocation.REPORT_TYPE_DEFAULT);
            }
        };

        locationCallbackOnDemand = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onLocationChanged(locationResult.getLastLocation(),MessageLocation.REPORT_TYPE_RESPONSE);
            }
        };

        setupNotificationChannels();
        startForeground(NOTIFICATION_ID_ONGOING, getOngoingNotification());

        setupLocationRequest();
        setupLocationPing();

        setupGeofences();

        eventBus.register(this);
        eventBus.postSticky(new Events.ServiceStarted());

        preferences.registerOnPreferenceChangedListener(this);

       // registerWifiStateReceiver(); Testing only
    }

    private void registerWifiStateReceiver() {
        Timber.v("registering broadcast receiver");
        //IntentFilter intentFilter = new IntentFilter();
        //intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        //intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        //WifiStateReceiver receiver = new WifiStateReceiver();
        //registerReceiver(receiver, intentFilter);

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();

        connectivityManager.registerNetworkCallback(
                builder.build(),
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        Timber.v("network available %s", network.toString());


                    }


                    @Override
                    public void onLost(Network network) {
                        Timber.v("network lost %s", network.toString());


                    }
                }

        );    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        preferences.unregisterOnPreferenceChangedListener(this);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            handleIntent(intent);
        }

        return START_STICKY;
    }

    private void handleIntent(@NonNull Intent intent) {

        if (intent.getAction() != null) {
            Timber.v("intent received with action:%s", intent.getAction());

            switch (intent.getAction()) {
                case INTENT_ACTION_SEND_LOCATION_USER:
                    locationProcessor.publishLocationMessage(MessageLocation.REPORT_TYPE_USER);
                    return;
                case INTENT_ACTION_SEND_EVENT_CIRCULAR:
                    onGeofencingEvent(GeofencingEvent.fromIntent(intent));
                    return;
                case INTENT_ACTION_CLEAR_NOTIFICATIONS:
                    clearEventStackNotification();
                    return;
                case INTENT_ACTION_REREQUEST_LOCATION_UPDATES:
                    setupLocationRequest();
                    return;
                case INTENT_ACTION_CHANGE_MONITORING:
                    if(intent.hasExtra(Preferences.Keys.MONITORING)) {
                        preferences.setMonitoring(intent.getIntExtra(Preferences.Keys.MONITORING, preferences.getMonitoring()));
                    } else {
                        // Step monitoring mode if no mode is specified
                        preferences.setMonitoringNext();
                    }
                    return;
                default:
                    Timber.v("unhandled intent action received: %s", intent.getAction());
            }
        }
    }

    private void setupNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        // Importance min will show normal priority notification for foreground service. See https://developer.android.com/reference/android/app/NotificationManager#IMPORTANCE_MIN
        // User has to actively configure this in the notification channel settings.
        NotificationChannel ongoingChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ONGOING, getString(R.string.notificationChannelOngoing), NotificationManager.IMPORTANCE_DEFAULT );
        ongoingChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ongoingChannel.setDescription(getString(R.string.notificationChannelOngoingDescription));
        ongoingChannel.enableLights(false);
        ongoingChannel.enableVibration(false);
        ongoingChannel.setShowBadge(false);
        ongoingChannel.setSound(null, null);
        notificationManager.createNotificationChannel(ongoingChannel);

        NotificationChannel eventsChannel = new NotificationChannel(NOTIFICATION_CHANNEL_EVENTS, getString(R.string.events), NotificationManager.IMPORTANCE_HIGH);
        eventsChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        eventsChannel.setDescription(getString(R.string.notificationChannelEventsDescription));
        eventsChannel.enableLights(false);
        eventsChannel.enableVibration(false);
        eventsChannel.setShowBadge(true);
        eventsChannel.setSound(null, null);
        notificationManager.createNotificationChannel(eventsChannel);
    }

    @Nullable
    private NotificationCompat.Builder getOngoingNotificationBuilder() {
        if (activeNotificationCompatBuilder != null)
            return activeNotificationCompatBuilder;


        activeNotificationCompatBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ONGOING);


        Intent resultIntent = new Intent(this, MapActivity.class);
        resultIntent.setAction("android.intent.action.MAIN");
        resultIntent.addCategory("android.intent.category.LAUNCHER");
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        activeNotificationCompatBuilder.setContentIntent(resultPendingIntent);
        activeNotificationCompatBuilder.setSortKey("a");


        Intent publishIntent = new Intent();
        publishIntent.setAction(INTENT_ACTION_SEND_LOCATION_USER);
        PendingIntent publishPendingIntent = PendingIntent.getService(this, 0, publishIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent changeMonitoringIntent = new Intent();
        publishIntent.setAction(INTENT_ACTION_CHANGE_MONITORING);
        PendingIntent changeMonitoringPendingIntent = PendingIntent.getService(this, 0, publishIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        activeNotificationCompatBuilder.addAction(R.drawable.ic_report_notification, getString(R.string.publish), publishPendingIntent).addAction(R.drawable.ic_report_notification, getString(R.string.notificationChangeMonitoring), changeMonitoringPendingIntent);
        activeNotificationCompatBuilder.setSmallIcon(R.drawable.ic_notification);
        activeNotificationCompatBuilder.setPriority(preferences.getNotificationHigherPriority() ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MIN);
        activeNotificationCompatBuilder.setSound(null, AudioManager.STREAM_NOTIFICATION);


        if (android.os.Build.VERSION.SDK_INT >= 23) {
            activeNotificationCompatBuilder.setColor(getColor(R.color.primary));
            activeNotificationCompatBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
            activeNotificationCompatBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }
        activeNotificationCompatBuilder.setOngoing(true);

        return activeNotificationCompatBuilder;
    }

    private void updateOngoingNotification() {
        notificationManager.notify(NOTIFICATION_ID_ONGOING,getOngoingNotification());
    }

    private Notification getOngoingNotification() {
        NotificationCompat.Builder builder = getOngoingNotificationBuilder();

        if (builder == null)
            return null;


        if (this.lastLocationMessage != null && preferences.getNotificationLocation()) {
            builder.setContentTitle(this.lastLocationMessage.getGeocoder());
            builder.setWhen(TimeUnit.SECONDS.toMillis(this.lastLocationMessage.getTst()));
            builder.setNumber(lastQueueLength);
        } else {
            builder.setContentTitle(getString(R.string.app_name));
        }

        // Show monitoring mode if endpoint state is not interesting
        if(lastEndpointState == MessageProcessor.EndpointState.CONNECTED || lastEndpointState == MessageProcessor.EndpointState.IDLE) {
            builder.setContentText(getMonitoringLabel(preferences.getMonitoring()));
        } else if (lastEndpointState == MessageProcessor.EndpointState.ERROR && lastEndpointState.getMessage() != null) {
            builder.setContentText(lastEndpointState.getLabel(this) + ": " + lastEndpointState.getMessage());
        } else {
            builder.setContentText( lastEndpointState.getLabel(this));
        }
        return builder.build();
    }


    private String getMonitoringLabel(int mode) {
        switch (mode) {
            case LocationProcessor.MONITORING_QUIET:
                return getString(R.string.monitoring_quiet);
            case LocationProcessor.MONITORING_MANUAL:
                return getString(R.string.monitoring_manual);
            case LocationProcessor.MONITORING_SIGNIFFICANT:
                return getString(R.string.monitoring_signifficant);
            case LocationProcessor.MONITORING_MOVE:
                return getString(R.string.monitoring_move);
        }
        return getString(R.string.na);
    }

    private void sendEventNotification(MessageTransition message) {
        NotificationCompat.Builder builder = getEventsNotificationBuilder();

        if (builder == null) {
            Timber.e("no builder returned");
            return;
        }

        FusedContact c = contactsRepo.getById(message.getContactKey());

        long when = TimeUnit.SECONDS.toMillis(message.getTst());
        String location = message.getDesc();

        if (location == null) {
            location = getString(R.string.aLocation);
        }
        String title = message.getTid();
        if (c != null)
            title = c.getFusedName();
        else if (title == null) {
            title = message.getContactKey();
        }

        String text = String.format("%s %s", getString(message.getTransition() == Geofence.GEOFENCE_TRANSITION_ENTER ? R.string.transitionEntering : R.string.transitionLeaving), location);


        eventsNotificationCompatBuilder.setContentTitle(title);
        eventsNotificationCompatBuilder.setContentText(text);
        eventsNotificationCompatBuilder.setWhen(TimeUnit.SECONDS.toMillis(message.getTst()));
        eventsNotificationCompatBuilder.setShowWhen(true);
        eventsNotificationCompatBuilder.setGroup(NOTIFICATION_GROUP_EVENTS);
        // Deliver notification
        Notification n = eventsNotificationCompatBuilder.build();

        Timber.v("sending new transition notification");
        notificationManagerCompat.notify(notificationEventsID++, n);
        //notificationManagerCompat.notify(NOTIFICATION_TAG_EVENTS_STACK, System.currentTimeMillis() / 1000, n) ;
        sendEventStackNotification(title, text, when);
    }


    private void sendEventStackNotification(String title, String text, long when) {
        if (Build.VERSION.SDK_INT >= 23) {
            Timber.v("SDK_INT >= 23, building stack notification");

            String whenStr = DateFormatter.formatDate(TimeUnit.MILLISECONDS.toSeconds((when)));

            Spannable newLine = new SpannableString(String.format("%s %s %s", whenStr, title, text));
            newLine.setSpan(new StyleSpan(Typeface.BOLD), 0, whenStr.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            activeNotifications.push(newLine);
            Timber.v("groupedNotifications: %s", activeNotifications.size());

            // since we assume the most recent notification was delivered just prior to calling this method,
            // we check that previous notifications in the group include at least 2 notifications
            if (activeNotifications.size() > 1) {

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_EVENTS);
                String summary = getResources().getQuantityString(R.plurals.notificationEventsTitle, activeNotifications.size());
                builder.setContentTitle(getString(R.string.events));
                builder.setContentText(summary);
                builder.setGroup(NOTIFICATION_GROUP_EVENTS); // same as group of single notifications
                builder.setGroupSummary(true);
                builder.setColor(getColor(R.color.primary));
                builder.setAutoCancel(true);
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                builder.setSmallIcon(R.drawable.ic_notification);
                builder.setDefaults(Notification.DEFAULT_ALL);
                // for every previously sent notification that met our above requirements,
                // insert a new line containing its title to the inbox style notification extender
                NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
                inbox.setSummaryText(summary);


                // Append new notification to existing
                CharSequence[] cs = null;

                if (stackNotification != null) {
                    cs = (CharSequence[]) stackNotification.extras.get(NotificationCompat.EXTRA_TEXT_LINES);
                }

                if (cs == null) {
                    cs = new CharSequence[0];
                }

                for (int i = 0; i < cs.length && i < 19; i++) {
                    inbox.addLine(cs[i]);
                }
                inbox.addLine(newLine);

                builder.setNumber(cs.length + 1);
                builder.setStyle(inbox);
                builder.setContentIntent(PendingIntent.getActivity(this, (int) System.currentTimeMillis() / 1000, new Intent(this, MapActivity.class), PendingIntent.FLAG_ONE_SHOT));
                builder.setDeleteIntent(PendingIntent.getService(this, INTENT_REQUEST_CODE_CLEAR_EVENTS, (new Intent(this, BackgroundService.class)).setAction(INTENT_ACTION_CLEAR_NOTIFICATIONS), PendingIntent.FLAG_ONE_SHOT));

                stackNotification = builder.build();
                notificationManagerCompat.notify(NOTIFICATION_GROUP_EVENTS, NOTIFICATION_ID_EVENT_GROUP, stackNotification);
            }
        }
    }

    private void clearEventStackNotification() {
        Timber.v("clearing notification stack");
        activeNotifications.clear();
    }
    // TODO: Move to somewere else
    private void setupLocationPing() {
        scheduler.scheduleLocationPing();
    }

    private void onGeofencingEvent(@Nullable final GeofencingEvent event) {
        if (event == null) {
            Timber.e("geofencingEvent null");
            return;
        }

        if (event.hasError()) {
            Timber.e("geofencingEvent hasError: %s", event.getErrorCode());
            return;
        }

        final int transition = event.getGeofenceTransition();
        for (int index = 0; index < event.getTriggeringGeofences().size(); index++) {
            WaypointModel w = waypointsRepo.get(Long.parseLong(event.getTriggeringGeofences().get(index).getRequestId()));
            if(w == null) {
                Timber.e("waypoint id %s not found for geofence event", event.getTriggeringGeofences().get(index).getRequestId());
                continue;
            }
            locationProcessor.onWaypointTransition(w, event.getTriggeringLocation(), transition, MessageTransition.TRIGGER_CIRCULAR);
        }
    }

    private void onLocationChanged(@Nullable Location location, @Nullable String reportType) {
        if(location == null) {
            Timber.e("no location provided");
            return;
        }
        Timber.v("location update received: tst:%s, acc:%s, lat:%s, lon:%s type:%s",location.getTime(), location.getAccuracy(), location.getLatitude(), location.getLongitude(), reportType);

        if (location.getTime() > locationRepo.getCurrentLocationTime()) {
            locationProcessor.onLocationChanged(location,reportType);
        }
    }

    @SuppressWarnings("MissingPermission")
    public void requestOnDemandLocationUpdate() {
        if (missingLocationPermission()) {
            Timber.e("missing location permission");
            return;
        }

        LocationRequest request = new LocationRequest();

        request.setNumUpdates(1);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setExpirationDuration(TimeUnit.MINUTES.toMillis(1));

        Timber.d("On demand location request");
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);
        client.requestLocationUpdates(request, locationCallbackOnDemand,  runner.getBackgroundHandler().getLooper());
    }

    @SuppressWarnings("MissingPermission")
    private void setupLocationRequest() {
        if (missingLocationPermission()) {
            Timber.e("missing location permission");
            return;
        }

        if (mFusedLocationClient == null) {
            Timber.e("FusedLocationClient not available");
            return;
        }
        int monitoring = preferences.getMonitoring();

        LocationRequest request = new LocationRequest();

        switch (monitoring) {
            case LocationProcessor.MONITORING_QUIET:
            case LocationProcessor.MONITORING_MANUAL:
                request.setInterval(TimeUnit.SECONDS.toMillis(preferences.getLocatorInterval()));
                request.setSmallestDisplacement(preferences.getLocatorDisplacement());
                request.setPriority(LocationRequest.PRIORITY_LOW_POWER);
                break;
            case LocationProcessor.MONITORING_SIGNIFFICANT:
                request.setInterval(TimeUnit.SECONDS.toMillis(preferences.getLocatorInterval()));
                request.setSmallestDisplacement(preferences.getLocatorDisplacement());
                request.setPriority(getLocationRequestPriority());
                break;
            case LocationProcessor.MONITORING_MOVE:
                request.setInterval(TimeUnit.SECONDS.toMillis(preferences.getMoveModeLocatorInterval()));
                request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                break;
        }
        Timber.d("location request params: mode %s, interval (s):%s, fastestInterval (s):%s, priority:%s, displacement (m):%s", monitoring, TimeUnit.MILLISECONDS.toSeconds(request.getInterval()), TimeUnit.MILLISECONDS.toSeconds(request.getFastestInterval()), request.getPriority(), request.getSmallestDisplacement());
        mFusedLocationClient.requestLocationUpdates(request, locationCallback,  runner.getBackgroundHandler().getLooper());
    }

    private int getLocationRequestPriority() {
        switch (preferences.getLocatorPriority()) {
            case 0:
                return LocationRequest.PRIORITY_NO_POWER;
            case 1:
                return LocationRequest.PRIORITY_LOW_POWER;
            case 3:
                return LocationRequest.PRIORITY_HIGH_ACCURACY;
            case 2:
            default:
                return LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
        }
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent geofeneIntent = new Intent(this, BackgroundService.class);
        geofeneIntent.setAction(INTENT_ACTION_SEND_EVENT_CIRCULAR);
        return PendingIntent.getBroadcast(this, INTENT_REQUEST_CODE_GEOFENCE, geofeneIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("MissingPermission")
    private void setupGeofences() {
        if (missingLocationPermission()) {
            Timber.e("missing location permission");
            return;
        }

        Timber.v("loader thread:%s, isMain:%s", Looper.myLooper(), Looper.myLooper() == Looper.getMainLooper());

        LinkedList<Geofence> geofences = new LinkedList<>();
        List<WaypointModel> loadedWaypoints = waypointsRepo.getAllWithGeofences();


        for (WaypointModel w : loadedWaypoints){
            Timber.v("id:%s, desc:%s, lat:%s, lon:%s, rad:%s", w.getId(), w.getDescription(), w.getGeofenceLatitude(), w.getGeofenceLongitude(), w.getGeofenceRadius());

            try {
                geofences.add(new Geofence.Builder()
                        .setRequestId(Long.toString(w.getId()))
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                        .setNotificationResponsiveness((int) TimeUnit.MINUTES.toMillis(2))
                        .setCircularRegion(w.getGeofenceLatitude(), w.getGeofenceLongitude(), w.getGeofenceRadius())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE).build());
            } catch (IllegalArgumentException e) {
                Timber.e(e, "Invalid geofence parameter");
            }
        }

        if (geofences.size() > 0) {
            GeofencingRequest.Builder b = new GeofencingRequest.Builder();
            b.setInitialTrigger(Geofence.GEOFENCE_TRANSITION_ENTER);
            GeofencingRequest request = b.addGeofences(geofences).build();
            mGeofencingClient.addGeofences(request, getGeofencePendingIntent());
        }
    }

    private boolean missingLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED;
    }

    private void removeGeofences() {
        mGeofencingClient.removeGeofences(getGeofencePendingIntent());
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(Events.WaypointAdded e) {
        locationProcessor.publishWaypointMessage(e.getWaypointModel()); // TODO: move to waypointsRepo
        if(e.getWaypointModel().hasGeofence()) {
            removeGeofences();
            setupGeofences();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(Events.WaypointUpdated e) {
        locationProcessor.publishWaypointMessage(e.getWaypointModel()); // TODO: move to waypointsRepo
        removeGeofences();
        setupGeofences();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(Events.WaypointRemoved e) {
        if(e.getWaypointModel().hasGeofence()) {
            removeGeofences();
            setupGeofences();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(Events.ModeChanged e) {
        removeGeofences();
        setupGeofences();
        setupLocationRequest();
        updateOngoingNotification();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(Events.MonitoringChanged e) {
        setupLocationRequest();
        updateOngoingNotification();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(MessageTransition message) {
        Timber.v("transition isIncoming:%s topic:%s", message.isIncoming(), message.getTopic());
        if (message.isIncoming())
            sendEventNotification(message);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(MessageLocation m) {
        Timber.v("MessageLocation received %s, %s, outgoing: %s, delivered: %s ", m, lastLocationMessage, m.isOutgoing(), m.isDelivered());
        if (m.isDelivered() && (lastLocationMessage == null || lastLocationMessage.getTst() <= m.getTst())) {
            this.lastLocationMessage = m;
            updateOngoingNotification();
            geocodingProvider.resolve(m, this);
        }
    }

    public void onGeocodingProviderResult(MessageLocation m) {
        if (m == lastLocationMessage) {
            updateOngoingNotification();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(sticky = true)
    public void onEvent(MessageProcessor.EndpointState state) {
        Timber.v(state.getError(), "endpoint state changed %s. Message: %s", state.getLabel(this), state.getMessage());
        this.lastEndpointState = state;
        updateOngoingNotification();
    }

    @SuppressWarnings("unused")
    @Subscribe(sticky = true)
    public void onEvent(Events.QueueChanged e) {
        this.lastQueueLength = e.getNewLength();
        updateOngoingNotification();
    }

    @SuppressWarnings("unused")
    @Subscribe(sticky = true)
    public void onEvent(Events.PermissionGranted event) {
        Timber.v("location permission granted");
        removeGeofences();
        setupGeofences();

        try {
            Timber.v("Getting last location");
            mFusedLocationClient.getLastLocation().addOnCompleteListener(this);
        } catch (SecurityException ignored) {
        }

    }

    private NotificationCompat.Builder getEventsNotificationBuilder() {
        if (!preferences.getNotificationEvents())
            return null;

        Timber.v("building notification builder");

        if (eventsNotificationCompatBuilder != null)
            return eventsNotificationCompatBuilder;

        Timber.v("builder not present, lazy building");
        eventsNotificationCompatBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_EVENTS);

        Intent openIntent = new Intent(this, MapActivity.class);
        openIntent.setAction("android.intent.action.MAIN");
        openIntent.addCategory("android.intent.category.LAUNCHER");
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        eventsNotificationCompatBuilder.setContentIntent(openPendingIntent);
        //eventsNotificationCompatBuilder.setDeleteIntent(ServiceProxy.getBroadcastIntentForService(this.context, ServiceProxy.SERVICE_NOTIFICATION, ServiceNotification.INTENT_ACTION_CANCEL_EVENT_NOTIFICATION, null));
        eventsNotificationCompatBuilder.setSmallIcon(R.drawable.ic_notification);
        eventsNotificationCompatBuilder.setAutoCancel(true);
        eventsNotificationCompatBuilder.setShowWhen(true);
        eventsNotificationCompatBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        eventsNotificationCompatBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
        eventsNotificationCompatBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            eventsNotificationCompatBuilder.setColor(getColor(R.color.primary));
        }

        return eventsNotificationCompatBuilder;
    }

    @Override
    public void onComplete(@NonNull Task<Location> task) {
        onLocationChanged(task.getResult(),MessageLocation.REPORT_TYPE_DEFAULT);
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onAttachAfterModeChanged() {
        //NOOP. Handled through eventbus
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Preferences.Keys.LOCATOR_INTERVAL.equals(key) ||
                Preferences.Keys.LOCATOR_DISPLACEMENT.equals(key) ||
                Preferences.Keys.LOCATOR_PRIORITY.equals(key) ||
                Preferences.Keys.LOCATOR_INTERVAL_MOVE_MODE.equals(key)
        ) {
            Timber.v("locator preferences changed. Resetting location request.");
            setupLocationRequest();
        }
    }

    public class LocalBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Timber.v("in onBind()");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Timber.v("Last client unbound from service");
    }
}
