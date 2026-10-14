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

    private String getMessage(ChannelMessageEvent event) {
        return event.getMessage();
    }

    private String getChannel(ChannelMessageEvent event) {
        return event.getChannel().getName();
    }

    private String getUsername(ChannelMessageEvent event) {
        return event.getUser().getName();
    }

    /*****************************COMMANDS***********************************************/
    private void handleTitleChange(ChannelMessageEvent event) {
        if(!hasPerms(event)) return;

        String newTitle = getMessage(event).substring("!title".length()).trim();

        twitchClient.getHelix().updateChannelInformation(token, broadID.getBroadcasterId(getChannel(event)), ChannelInformation.builder()
                .title(newTitle)
                .build()).execute();

        twitchClient.getChat().sendMessage(getChannel(event), "Title is now: " + newTitle);
    }

    private void handleCategoryChange(ChannelMessageEvent event) {
        if(!hasPerms(event)) return;

        String categoryTitle = getMessage(event).substring("!game ".length()).trim();

        GameList games = twitchClient.getHelix().getGames(token, null, Collections.singletonList(categoryTitle), null).execute();

        if(games.getGames().isEmpty()) {
            twitchClient.getChat().sendMessage(getChannel(event), "Category not found: " + categoryTitle);
            return;
        }

        String gameId = games.getGames().getFirst().getId();

        twitchClient.getHelix().updateChannelInformation(token, broadID.getBroadcasterId(getChannel(event)), ChannelInformation.builder()
                .gameId(gameId)
                .build()).execute();

        twitchClient.getChat().sendMessage(getChannel(event), "Game is now: " + categoryTitle);

    }

    // This token along with title & game change has expired?
    private void handleGetClip(ChannelMessageEvent event) {

        try {
            ClipList clipList = twitchClient.getHelix().getClips(token, broadID.getBroadcasterId(getChannel(event)), null, null, null, null,
                    1, null, null, null).execute();

            clipList.getData().forEach(clip -> {
                String clipURL = "https://clips.twitch.tv/" + clip.getId();
                System.out.println("Clip found: " + clip.getId());
                twitchClient.getChat().sendMessage(getChannel(event), "Clip found: " + clipURL);
            });

        } catch (HystrixRuntimeException e) {
            System.out.println("Could not fetch clip" + e.getCause());
            twitchClient.getChat().sendMessage(getChannel(event), "Clip was not found.");
        } finally {
            System.out.println("Cleaning up");
        }

    }


    private void addCustomCommands(ChannelMessageEvent event) {
        if (!hasPerms(event)) return;

        if (getMessage(event).startsWith("!addcom")) {
            String[] parts = getMessage(event).split(" ", 3);

            if (parts.length < 3) {
                twitchClient.getChat().sendMessage(getChannel(event), "@" + getUsername(event) + " Template: !addcom <name> <response>");
                return;
            }

            String commandName = parts[1].startsWith("!") ? parts[1].substring(1) : parts[1];
            commandName = commandName.toLowerCase();
            String response = parts[2];

            customCommandsMap.put(commandName, response);
            twitchClient.getChat().sendMessage(getChannel(event), "@" + getUsername(event) + " Command !" + commandName + " added!");
        }
    }

    private void deleteCustomCommands(ChannelMessageEvent event) {
        if(!hasPerms(event)) return;

        String[] parts = getMessage(event).split(" ", 2);

        if(parts.length < 2) {
            twitchClient.getChat().sendMessage(getChannel(event), "@" + getUsername(event) + " Template: !delcom <name>");
            return;
        }

        String commandName = parts[1].startsWith("!") ? parts[1].substring(1) : parts[1];
        commandName = commandName.toLowerCase();


        if (!customCommandsMap.containsKey(commandName)) {
            twitchClient.getChat().sendMessage(getChannel(event), "@" + getChannel(event) +" Command " + commandName + " does not exist");
            return;
        }

        customCommandsMap.remove(commandName);
        twitchClient.getChat().sendMessage(getChannel(event),  "@" + getChannel(event) + " Command " + commandName +  " has been deleted");
    }

    private void handleCustomCommand(ChannelMessageEvent event) {
        if (getMessage(event) == null || !getMessage(event).startsWith("!") || getMessage(event).length() < 2) {
            return;
        }

        String commandTrigger = getMessage(event).split(" ", 2)[0].substring(1).toLowerCase();

        if (customCommandsMap.containsKey(commandTrigger)) {
            String response = customCommandsMap.get(commandTrigger);
            twitchClient.getChat().sendMessage(getChannel(event), response);
        }
    }

}
