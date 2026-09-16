package twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.FollowEvent;
import io.github.cdimascio.dotenv.Dotenv;

public class ClientBuilder {

    Dotenv dotenv = Dotenv.load();
    private final String token = dotenv.get("ACCESS_TOKEN");
    private final String clientID = dotenv.get("CLIENT_ID");
    private final String clientSECRET = dotenv.get("CLIENT_SECRET");

    OAuth2Credential credential = new OAuth2Credential("twitch", token);

    TwitchClient twitchClient = TwitchClientBuilder.builder()
            .withClientId(clientID)
            .withClientSecret(clientSECRET)
            .withEnableHelix(true)
            .withEnableChat(true)
            .withChatAccount(credential)
            .withDefaultEventHandler(SimpleEventHandler.class)
            .withCommandTrigger("!")
            .withEnableEventSocket(true)
            .withDefaultAuthToken(new OAuth2Credential("twitch", token))
            .build();

    EventManager eventManager = twitchClient.getEventManager();

    BroadcasterId broadID = new BroadcasterId(twitchClient);
    Commands commands = new Commands(twitchClient, broadID);
    ModCommands mcmd = new ModCommands(twitchClient, broadID);

    public void initiate() {
        joinChannel("SirUncleIroh"); // refactor this to take any channel
        eventListeners();
        registerHandlers();
        spyChat();
        commands.Command();
        mcmd.ModCom();
    }


    public void eventListeners() {
        twitchClient.getClientHelper().enableStreamEventListener("twitch4j");
        twitchClient.getClientHelper().enableFollowEventListener("twitch4j");
        twitchClient.getClientHelper().enableClipEventListener("twitch4j");
    }

    public void joinChannel(String channelName) {
        twitchClient.getChat().joinChannel(channelName);
    }

    public void registerHandlers() {
        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event ->
                System.out.println(event.getUser().getName() + ": " + event.getMessage()));

                twitchClient.getEventManager().onEvent(FollowEvent.class, event ->
                        System.out.println(event.getUser().getName() + " followed! Thank you!"));
    }

    public void spyChat() {
        eventManager.onEvent(ChannelMessageEvent.class, event -> {
            System.out.println("[" + event.getChannel().getName() + "] " + event.getUser().getName() + ": " + event.getMessage());
        });
    }

}
