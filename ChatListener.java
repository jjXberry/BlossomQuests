package com.blossomsmp.quests.listeners;

import com.blossomsmp.quests.BlossomQuests;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Catches answers to menu questions (how many items, how much money).
 * Uses the older chat event on purpose: Paper fires it before the new one,
 * so chat plugins never see (or broadcast) the answer.
 */
@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final BlossomQuests plugin;

    public ChatListener(BlossomQuests plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getPrompts().handle(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
            try {
                event.getRecipients().clear();
            } catch (UnsupportedOperationException ignored) {
                // read-only on some servers; cancelling is enough
            }
        }
    }
}
