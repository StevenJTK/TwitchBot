import twitch.ClientBuilder;
import twitch.ModCommands;

public class Main {
    public static void main(String[] args) {
        ClientBuilder build = new ClientBuilder();

        build.initiate();
    }
}
