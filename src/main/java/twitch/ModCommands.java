package twitch;


import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.enums.CommandPermission;
import com.github.twitch4j.helix.domain.*;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ModCommands {

    Dotenv dotenv = Dotenv.load();
    private final String token = dotenv.get("ACCESS_TOKEN");
    private BroadcasterId broadID;
    private final Map<String, Consumer<ChannelMessageEvent>> modCommandMap = new HashMap<>();
    private final Map<String, String> customCommandsMap = new HashMap<>();
    private final TwitchClient twitchClient;

    public ModCommands(TwitchClient twitchClient, BroadcasterId broadID) {
        this.twitchClient = twitchClient;
        addToModMap();
    }

    public void ModCom() {
        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            String message = event.getMessage();

            String command = message.split(" ", 2)[0].substring(1).toLowerCase();

            if (modCommandMap.containsKey(command)) {
                modCommandMap.get(command).accept(event);
            } else {
                handleCustomCommand(event);
            }
        });
    }

    private void addToModMap() {
        modCommandMap.put("title", this::handleTitleChange);
        modCommandMap.put("game", this::handleCategoryChange);
        modCommandMap.put("last", this::handleGetClip);
        modCommandMap.put("addcom", this::addCustomCommands);
        modCommandMap.put("delcom", this:: deleteCustomCommands);
    }

    private boolean hasPerms(ChannelMessageEvent event) {
       return event.getPermissions().contains(CommandPermission.MODERATOR) || event.getPermissions().contains(CommandPermission.BROADCASTER);
    }

    /*****************************COMMANDS***********************************************/
    private void handleTitleChange(ChannelMessageEvent event) {
        if(!hasPerms(event)) return;

        String message = event.getMessage();
        String channel = event.getChannel().getName();
        String newTitle = message.substring("!title".length()).trim();

        String broadcasterId = broadID.getBroadcasterId(channel);

        twitchClient.getHelix().updateChannelInformation(token, broadcasterId, ChannelInformation.builder()
                .title(newTitle)
                .build()).execute();

        twitchClient.getChat().sendMessage(channel, "Title is now: " + newTitle);
    }

    private void handleCategoryChange(ChannelMessageEvent event) {
        if(!hasPerms(event)) return;

        String channel = event.getChannel().getName();
        String message = event.getMessage();
        String categoryTitle = message.substring("!game ".length()).trim();

        GameList games = twitchClient.getHelix().getGames(token, null, Collections.singletonList(categoryTitle), null).execute();

        if(games.getGames().isEmpty()) {
            twitchClient.getChat().sendMessage(channel, "Category not found: " + categoryTitle);
            return;
        }

        String gameId = games.getGames().getFirst().getId();
        String broadcasterId = broadID.getBroadcasterId(channel);

        twitchClient.getHelix().updateChannelInformation(token, broadcasterId, ChannelInformation.builder()
                .gameId(gameId)
                .build()).execute();

        twitchClient.getChat().sendMessage(channel, "Game is now: " + categoryTitle);

    }

    // defaults to most viewed clip
    private void handleGetClip(ChannelMessageEvent event) {
        String channel = event.getChannel().getName();
        String broadcasterId = broadID.getBroadcasterId(channel);

        try {
            ClipList clipList = twitchClient.getHelix().getClips(token, broadcasterId, null, null, null, null,
                    1, null, null, null).execute();

            clipList.getData().forEach(clip -> {
                String clipURL = "https://clips.twitch.tv/" + clip.getId();
                System.out.println("Clip found: " + clip.getId());
                twitchClient.getChat().sendMessage(channel, "Clip found: " + clipURL);
            });

        } catch (HystrixRuntimeException e) {
            System.out.println("Could not fetch clip" + e.getCause());
            twitchClient.getChat().sendMessage(channel, "Clip was not found.");
        } finally {
            System.out.println("Cleaning up");
        }

    }


    private void addCustomCommands(ChannelMessageEvent event) {
        if (!hasPerms(event)) return;

        String channel = event.getChannel().getName();
        String message = event.getMessage();
        String user = event.getUser().getName();


        if (message.startsWith("!addcom")) {
            String[] parts = message.split(" ", 3);

            if (parts.length < 3) {
                twitchClient.getChat().sendMessage(channel, "@" + user + " Template: !addcom <name> <response>");
                return;
            }

            String commandName = parts[1].startsWith("!") ? parts[1].substring(1) : parts[1];
            commandName = commandName.toLowerCase();
            String response = parts[2];

            customCommandsMap.put(commandName, response);
            twitchClient.getChat().sendMessage(channel, "@" + user + " Command !" + commandName + " added!");
        }
    }

    private void deleteCustomCommands(ChannelMessageEvent event) {
        if(!hasPerms(event)) return;

        String channel = event.getChannel().getName();
        String user =  event.getChannel().getName();
        String message = event.getMessage();

        String[] parts = message.split(" ", 2);

        if(parts.length < 2) {
            twitchClient.getChat().sendMessage(channel, "@" + user + " Template: !delcom <name>");
            return;
        }

        String commandName = parts[1].startsWith("!") ? parts[1].substring(1) : parts[1];
        commandName = commandName.toLowerCase();


        if (!customCommandsMap.containsKey(commandName)) {
            twitchClient.getChat().sendMessage(channel, "@" + user +" Command " + commandName + " does not exist");
            return;
        }

        customCommandsMap.remove(commandName);
        twitchClient.getChat().sendMessage(channel,  "@" + user + " Command " + commandName +  " has been deleted");
    }

    private void handleCustomCommand(ChannelMessageEvent event) {
        String channel = event.getChannel().getName();
        String message = event.getMessage();

        if (message == null || !message.startsWith("!") || message.length() < 2) {
            return;
        }

        String commandTrigger = message.split(" ", 2)[0].substring(1).toLowerCase();

        if (customCommandsMap.containsKey(commandTrigger)) {
            String response = customCommandsMap.get(commandTrigger);
            twitchClient.getChat().sendMessage(channel, response);
        }
    }

}
