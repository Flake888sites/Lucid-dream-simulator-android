package hub;

import java.util.HashMap;
import java.util.Map;

/** Fixed hand-written dialogue tree. No generation, no free text. */
public final class Dialogue {
    public record Node(String text, String[] labels, String[] targets) {}
    private static final Map<String, Node> N = new HashMap<>();
    private static void put(String id, String t, String[] l, String[] g) { N.put(id, new Node(t, l, g)); }
    private static void leaf(String id, String parent, String t) {
        put(id, t, new String[]{"Back", "End conversation"}, new String[]{parent, "END"});
    }

    static {
        put("root", "Welcome, dreamer. I am the Guide. Show me an image and I will unfold it into a world. What would you like to know?",
            new String[]{"How does the generation work?", "The land: plains, lakes and forests", "About the creatures", "The sleep states",
                "What about the music?", "Controls", "End conversation"},
            new String[]{"gen", "land", "creatures", "sleep", "music", "controls", "END"});

        leaf("gen", "root", "Your image is shrunk to 64x64 and hashed with its file size. That hash seeds every noise function. Brightness shapes the relief, edges decide how dense the forests and structures grow, saturation drives the dream effects, symmetry mirrors the land. The same image always gives the same world.");
        leaf("land", "root", "A slow noise map decides where the land is open plains and where it is rugged highland. Plains are gentle meadows with grass tufts, flowers and the odd grove. The highlands are steep and wooded. Lakes only fill the deepest valleys, so a dark image makes more lakes than a bright one, but never a whole ocean.");
        leaf("music", "root", "The dominant colour picks the key, brightness the tempo, saturation the timbre, edge density the rhythm. Crowds add percussion, forests add bass and shadow, water adds soft noise. Everything is raw sine waves and noise, synthesized on the fly.");
        leaf("controls", "root", "WASD to move, Space to jump, Shift to run, the mouse to look. In the hub, E interacts and L loads a new image. In a dream, H returns to the hub and Q reads the mind of the creature you are looking at. ESC opens the menu with graphics settings (render distance, vegetation, distortion and more).");

        put("creatures", "Every creature grows from its own seed: body, voice, personality and mind. Which part would you like to hear about?",
            new String[]{"What do their bodies look like?", "How do they speak?", "How do they think?", "What can they build or destroy?",
                "Reading and editing minds (Q)", "Back", "End conversation"},
            new String[]{"forms", "speech", "mind", "craft", "qmind", "root", "END"});
        leaf("forms", "creatures", "There are eight body plans. Walkers stand on two legs with arms. Striders trot on four legs with tails. Coils are long serpents. Drifters float like jellyfish, with tentacles and orbiting lights. Skitterers are spiders with six or eight legs. Flappers have wings and beaks. Reachers wander on stilts with long necks. Lumps are heaps of blobs with spikes. Proportions, one to five eyes, horns and colours differ for every single creature.");
        leaf("speech", "creatures", "Each creature has its own little language, made from random syllables, and its own word for every idea it knows. It speaks in short bursts and the ending shows its mood: one exclamation mark for fear, two for anger, a question mark for curiosity and dots for calm. Those same words appear in its thoughts when you read its mind.");
        leaf("mind", "creatures", "Four moods - fear, curiosity, aggression and sociability - are pushed around by light, trees, neighbours, the sleep state and what the creature believes about you. It also holds twelve ideas, like water, building or music, each learned from experience. What it understands changes what it does: a creature that knows water will not wade in, and one that has never heard of the dark fears it.");
        leaf("craft", "creatures", "Creatures who understand building collect wood from trees, stone from rocks and crystals, then raise huts, towers, walls and monuments near their home. Those who understand destroying and feel aggressive may tear down trees or other creatures' work, and the owner will remember. Some dance if they know music. Trees they cut down stay gone.");
        leaf("qmind", "creatures", "Look at a creature and press Q. The Imagination tab shows pictures of what it is picturing and the stream of its thoughts. The Mind editor lets you teach or erase ideas, nudge its personality, change how it sees you, and leave gifts of wood, stone or crystal. Change its mind, then watch what it does.");

        put("sleep", "Each portal in the hub opens a different way of sleeping. They are not deeper and deeper, just stranger and stranger. Which one?",
            new String[]{"Wakefulness", "Light Sleep", "Deep Sleep", "REM", "Back", "End conversation"},
            new String[]{"s0", "s1", "s2", "s3", "root", "END"});
        leaf("s0", "sleep", "Wakefulness: the world is clear and calm, exactly as the image made it. Walk around before you try the others, so you know what changes.");
        leaf("s1", "sleep", "Light Sleep: gravity forgets itself. Hills peel away into drifting islands, everything leans, jumps become slow floats and the colours turn iridescent. Creatures hover above the ground.");
        leaf("s2", "sleep", "Deep Sleep: the world folds inside out. Hills become hollows, towers twist, colours flash to their opposites, your jumps become enormous and time slows down for the creatures, whose bodies stretch and shrink.");
        leaf("s3", "sleep", "REM: a kaleidoscope. The ground ripples in petals, space swirls, colours cycle in rainbow bands, gravity breathes in and out, your view pulses, and the creatures race about, throbbing.");
    }

    public static Node get(String id) { return N.getOrDefault(id, N.get("root")); }
}
