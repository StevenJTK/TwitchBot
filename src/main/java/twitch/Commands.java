package twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.CommandEvent;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.helix.domain.ChannelInformation;
import com.github.twitch4j.helix.domain.InboundFollowers;
import com.github.twitch4j.helix.domain.UserList;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


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
/*****************************************************************/
    private void handleTitleChange(ChannelMessageEvent event) {
        String message = event.getMessage();
        String channel = event.getChannel().getName();

        if (!message.startsWith("!title ")) return;

        String newTitle = message.substring("!title ".length()).trim();

        UserList users = twitchClient.getHelix().getUsers(token, null, Collections.singletonList("SirUncleIroh")).execute();
        String broadcasterId = users.getUsers().getFirst().getId();

        twitchClient.getHelix().updateChannelInformation(token, broadcasterId, ChannelInformation.builder()
                .title(newTitle)
                .build()).execute();

        twitchClient.getChat().sendMessage(channel, "Title updated to: " + newTitle);

    }
}
