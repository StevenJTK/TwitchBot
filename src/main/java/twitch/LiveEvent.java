package twitch;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;

public class LiveEvent {

    private TwitchClient twitchClient;
    private EventManager eventManager;
    private BroadcasterId broadID;

    public LiveEvent(TwitchClient twitchClient, BroadcasterId broadID) {
        this.twitchClient = twitchClient;
        this.eventManager = twitchClient.getEventManager();
        this.broadID = broadID;
        liveListener();
    }

    // works - test again tho
    private void liveListener() {
        eventManager.onEvent(ChannelGoLiveEvent.class, event -> {
            String channel = event.getChannel().getName();
            System.out.println("Channel: " + event.getChannel().getName() + " is now live!");
            twitchClient.getChat().sendMessage(channel, "We are live!");
        });

        eventManager.onEvent(ChannelGoOfflineEvent.class, event -> {
            String channel = event.getChannel().getName();
            System.out.println("Channel:" + event.getChannel().getName() + " is now offline.");
            twitchClient.getChat().sendMessage(channel, "Streamer is now offline. Thanks for tuning in!");
        });
    }

    // Future Test - works add custom message
    public void monitorChannel(String channel) {
        String broadcasterId = broadID.getBroadcasterId(channel);

        boolean success = twitchClient.getClientHelper().enableStreamEventListener(broadcasterId, channel);

        if(!success) {
            System.out.println("Could not listen for: " + channel);
        }
    }
}
