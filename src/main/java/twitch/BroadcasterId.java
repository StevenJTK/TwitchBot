package twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.domain.UserList;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BroadcasterId {

    Dotenv dotenv = Dotenv.load();
    private final TwitchClient twitchClient;
    private final String token = dotenv.get("ACCESS_TOKEN");
    private Map<String, String> broadcasterId = new HashMap<>();

    public BroadcasterId(TwitchClient twitchClient) {
        this.twitchClient = twitchClient;
    }

    String getBroadcasterId(String channelName) {
        return broadcasterId.computeIfAbsent(channelName, name -> {
            UserList users = twitchClient.getHelix().getUsers(token, null, Collections.singletonList(name)).execute();
            return users.getUsers().getFirst().getId();
        });
    }

}
