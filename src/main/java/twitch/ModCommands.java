package twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.enums.CommandPermission;
import com.github.twitch4j.helix.domain.ChannelInformation;
import com.github.twitch4j.helix.domain.GameList;
import com.github.twitch4j.helix.domain.UserList;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ModCommands {

    Dotenv dotenv = Dotenv.load();
    private final String token = dotenv.get("ACCESS_TOKEN");
    private final Map<String, Consumer<ChannelMessageEvent>> modCommandMap = new HashMap<>();
    private final TwitchClient twitchClient;

    public ModCommands(TwitchClient twitchClient) {
        this.twitchClient = twitchClient;
        addToModMap();
    }

    public void ModCom() {
        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            String message = event.getMessage();
            String user = event.getUser().getName();

            String command = message.split(" ", 2)[0].substring(1).toLowerCase();

            if (modCommandMap.containsKey(command)) {
                modCommandMap.get(command).accept(event);
            }
        });
    }

    private void addToModMap() {
        modCommandMap.put("title", this::handleTitleChange);
        modCommandMap.put("game", this::handleCategoryChange);
    }

    private void hasPerms(ChannelMessageEvent event) {
        boolean isModOrBroadCaster = event.getPermissions().contains(CommandPermission.MODERATOR) || event.getPermissions().contains(CommandPermission.BROADCASTER);
        if(!isModOrBroadCaster) return;
    }

    private void handleTitleChange(ChannelMessageEvent event) {
        String message = event.getMessage();
        String channel = event.getChannel().getName();

        hasPerms(event);

        String newTitle = message.substring("!title".length()).trim();

        UserList users = twitchClient.getHelix().getUsers(token, null, Collections.singletonList("SirUncleIroh")).execute();
        String broadcasterId = users.getUsers().getFirst().getId();

        twitchClient.getHelix().updateChannelInformation(token, broadcasterId, ChannelInformation.builder()
                .title(newTitle)
                .build()).execute();

        twitchClient.getChat().sendMessage(channel, "Title is now: " + newTitle);
    }

    private void handleCategoryChange(ChannelMessageEvent event) {
        String channel = event.getChannel().getName();
        String message = event.getMessage();
        String categoryTitle = message.substring("!game ".length()).trim();

        hasPerms(event);

        UserList users = twitchClient.getHelix().getUsers(token, null, Collections.singletonList("SirUncleIroh")).execute();

        GameList games = twitchClient.getHelix().getGames(token, null, Collections.singletonList(categoryTitle), null).execute();

        if(games.getGames().isEmpty()) {
            twitchClient.getChat().sendMessage(channel, "Category not found: " + categoryTitle);
            return;
        }

        String gameId = games.getGames().getFirst().getId();
        String broadcasterId = users.getUsers().getFirst().getId();

        twitchClient.getHelix().updateChannelInformation(token, broadcasterId, ChannelInformation.builder()
                .gameId(gameId)
                .build()).execute();

        twitchClient.getChat().sendMessage(channel, "Game is now: " + categoryTitle);

    }


}
