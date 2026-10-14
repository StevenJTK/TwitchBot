package twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.CommandEvent;
import com.github.twitch4j.helix.domain.*;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.github.cdimascio.dotenv.Dotenv;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;


public class Commands {

    Dotenv dotenv = Dotenv.load();
    private final String token = dotenv.get("ACCESS_TOKEN");
    private final TwitchClient twitchClient;
    private final BroadcasterId broadID;
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
        commandMap.put("followage", this::handleFollowAge);
    }

    public Commands(TwitchClient twitchClient, BroadcasterId broadID) {
        this.twitchClient = twitchClient;
        this.broadID = broadID;
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

    private String getUsername(CommandEvent event) {
        return event.getUser().getName();
    }

    private String getUserID(CommandEvent event) {
        return event.getUser().getId();
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
        twitchClient.getChat().sendMessage(channel, "@" + getUsername(event) + " Thank you for the lurk!");
    }

    private void handleUnlurk(String channel, CommandEvent event) {
        twitchClient.getChat().sendMessage(channel, "@" + getUsername(event) + " Welcome back! <3");
    }

    private void handleFollowersCheck(String channel, CommandEvent event) {
        InboundFollowers result = twitchClient.getHelix()
              .getChannelFollowers(token, broadID.getBroadcasterId(channel), null, 1, null ).execute();

      int followerCount = result.getTotal();

      twitchClient.getChat().sendMessage(channel, "Amount of followers: " + followerCount);
    }


    private void handleCreateClips(String channel, CommandEvent event) {
        StreamList streams = twitchClient.getHelix().getStreams(token, null, null, 1, null,
                null, null, Collections.singletonList(channel)).execute();

        if(streams.getStreams().isEmpty()) {
            twitchClient.getChat().sendMessage(channel, "Broadcaster is not live.");
            return;
        }

        try {
            CreateClipList clipData = twitchClient.getHelix().createClip(token, broadID.getBroadcasterId(channel), false).execute();
            clipData.getData().forEach(clip -> {
                twitchClient.getChat().sendMessage(channel, "@" + getUsername(event) + " Clip created!");
            });

        } catch (HystrixRuntimeException e) {
            twitchClient.getChat().sendMessage(channel, "Clip could not be created.");
        } finally {
            System.out.println("Cleaning up");
        }
    }


    private void handleFollowAge(String channel, CommandEvent event) {
        InboundFollowers result = twitchClient.getHelix()
                .getChannelFollowers(token, broadID.getBroadcasterId(channel), getUserID(event), null, null ).execute();

        if(getUserID(event).equals(broadID.getBroadcasterId(channel))) {
            twitchClient.getChat().sendMessage(channel, "Broadcasters do not follow themselves silly!");
            return;
        }

        if(result.getFollows().isEmpty()) {
            twitchClient.getChat().sendMessage(channel,  "@" + getUserID(event) + " You're not following this channel. Follow or else!");
            return;
        }

        Instant followedAt = result.getFollows().getFirst().getFollowedAt();
        LocalDate followDate = followedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        Period period = Period.between(followDate, now);

        String results = String.format("%d years, %d months, %d days", // convert to stringbuilder?
                period.getYears(), period.getMonths(), period.getDays());
        twitchClient.getChat().sendMessage(channel, "@" + getUsername(event) + " You've been following since " + results);

    }
}
