package anticope.chatgames;

import anticope.chatgames.modules.AutoChatGame;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.DisplayItemUtils;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatGamesAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("ChatGames");
    public static final Category CATEGORY = new Category("Chat Games", () -> DisplayItemUtils.toStack(Items.BOOK));

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Chat Games Addon");

        // Modules
        Modules modules = Modules.get();
        modules.add(new AutoChatGame());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "anticope.chatgames";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Antigravity", "meteor-chat-games");
    }

    @Override
    public String getWebsite() {
        return "https://github.com/Antigravity/meteor-chat-games";
    }
}
