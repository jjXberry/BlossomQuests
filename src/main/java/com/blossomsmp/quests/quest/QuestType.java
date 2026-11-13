package com.blossomsmp.quests.quest;

/** What a quest asks you to do. The counter is the lifetime stat used by achievements. */
public enum QuestType {
    MINE("Mine", "mine"),
    PLACE("Place", "place"),
    KILL("Kill", "kill_mob"),
    KILL_PLAYER("Kill", "kill_player"),
    FISH("Catch", "fish"),
    CRAFT("Craft", "craft"),
    BREED("Breed", "breed"),
    PLAYTIME("Play for", "playtime"),
    BOSS("Defeat", "boss"),
    /** BlossomBiomes: walking into a Blossom biome for the first time (target = biome id) */
    DISCOVER("Discover", "biome_discover"),
    /** BlossomBiomes: picking up a biome material (target = material id) */
    GATHER("Gather", "biome_gather"),
    /** BlossomBiomes: helping beat a biome boss (target = boss id) */
    BIOME_BOSS("Defeat", "biome_boss");

    private final String verb;
    private final String counter;

    QuestType(String verb, String counter) {
        this.verb = verb;
        this.counter = counter;
    }

    public String verb() {
        return verb;
    }

    public String counter() {
        return counter;
    }
}
