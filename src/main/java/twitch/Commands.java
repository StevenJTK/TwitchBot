package twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.CommandEvent;
import com.github.twitch4j.helix.domain.CreateClipList;
import com.github.twitch4j.helix.domain.GameList;
import com.github.twitch4j.helix.domain.InboundFollowers;
import com.github.twitch4j.helix.domain.UserList;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;


public class Commands {

    Dotenv dotenv = Dotenv.load();
    private final String token = dotenv.get("ACCESS_TOKEN");
    private final TwitchClient twitchClient;
    private final Map<String, BiConsumer<String, CommandEvent>> commandMap = new HashMap<>();
    private final Map<String, Long> cooldownTimer = new HashMap<>();
    private static final long COOLDOWN_MS = 5000;


    private void addToMap() {
        commandMap.put("ping", this::handlePing);
        commandMap.put("gondor", this::handleGondor);
        commandMap.put("challenge", this::handleRegionLockChallenge);
        commandMap.put("zone", this::handleCurrentZone);
        commandMap.put("lurk", this::handleLurk);
        commandMap.put("unlurk", this::handleUnlurk);
        commandMap.put("followers", this::handleFollowersCheck);
        commandMap.put("clip", this::handleCreateClips);
    }

    public Commands(TwitchClient twitchClient) {
        this.twitchClient = twitchClient;
        addToMap();
    }

    public void Command() {
        twitchClient.getEventManager().onEvent(CommandEvent.class, event -> {
        String command = event.getCommand();
        String channel = event.getSourceId();

        if(isOnCooldown(command)) {
            return;
        }

        commandMap.getOrDefault(command, (ch, e) -> {}).accept(channel, event);
        cooldownTimer.put(command, System.currentTimeMillis());
        });
    }

    private boolean isOnCooldown(String command) {
        Long cd = cooldownTimer.get(command);
        if(cd == null) return false;
        return (System.currentTimeMillis() - cd) < COOLDOWN_MS;
    }

    /************* COMMANDS *************/

    private void handlePing(String channel, CommandEvent event) {
        twitchClient.getChat().sendMessage(channel, "Pong");
    }

    private void handleGondor(String channel, CommandEvent event) {
        twitchClient.getChat().sendMessage(channel, "WHERE WAS GONDOR");
    }

    private void handleRegionLockChallenge(String channel, CommandEvent event) {
        twitchClient.getChat().sendMessage(channel, "Challenge rules -> https://postimg.cc/kBc2Jm35");
    }

    private void handleCurrentZone(String channel, CommandEvent event) {
        twitchClient.getChat().sendMessage(channel, "Currently working on Khenarti's Roost");
    }

    private void handleLurk(String channel, CommandEvent event) {
        String username = event.getUser().getName();
        twitchClient.getChat().sendMessage(channel, "@" + username + " Thank you for the lurk!");
    }

    private void handleUnlurk(String channel, CommandEvent event) {
        String username = event.getUser().getName();
        twitchClient.getChat().sendMessage(channel, "@" + username + " Welcome back! <3");
    }

    private void handleFollowersCheck(String channel, CommandEvent event) {
      UserList users = twitchClient.getHelix().getUsers(token, null, Collections.singletonList("SirUncleIroh")).execute();

        String broadcasterId = users.getUsers().getFirst().getId();

        InboundFollowers result = twitchClient.getHelix()
              .getChannelFollowers(token, broadcasterId, null, 1, null ).execute();

      int followerCount = result.getTotal();

      twitchClient.getChat().sendMessage(channel, "Amount of followers: " + followerCount);
    }


    private void handleCreateClips(String channel, CommandEvent event) {
        UserList users = twitchClient.getHelix().getUsers(token, null, Collections.singletonList("SirUncleIroh")).execute();

        String broadcasterId = users.getUsers().getFirst().getId();

        try {
            CreateClipList clipData = twitchClient.getHelix().createClip(token, broadcasterId, false).execute();
            clipData.getData().forEach(clip -> {
                System.out.println("Clip created!" + clip.getId());
            });

        } catch (HystrixRuntimeException e) { // if channel note live = not live message / if clip could not be created seperate message?
            System.out.println("Could not create clip" + e.getCause());
            twitchClient.getChat().sendMessage(channel, "WE AINT LIVE");
        } finally {
            System.out.println("Cleaning up");
        }
    }
}
