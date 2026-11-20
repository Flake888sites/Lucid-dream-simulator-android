package creature;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A creature's inner life: what it knows, what it believes, what it pictures and what it is thinking. The player can read and edit it. */
public final class Mind {
    public static final int WATER = 0, TREES = 1, LIGHT = 2, DARK = 3, DREAMER = 4, OTHERS = 5, MUSIC = 6,
            CRYSTALS = 7, BUILD = 8, DESTROY = 9, SLEEP = 10, SPEECH = 11, N = 12;
    public static final String[] LABEL = {"water", "the forest", "light", "darkness", "the dreamer", "the crowd",
            "music", "crystal", "building", "destroying", "sleep", "speech"};
    public static final String[] FACT = {
            "Blue and cold. Do not step in it.", "Tall, still, and full of wood.", "Warm. Things look kind in it.",
            "Where shapes hide. It always passes.", "A huge strange visitor from outside.", "Others like me. Warmer together.",
            "A sound from everywhere at once.", "Hard, shiny, humming. Precious.", "Things can be stacked into a home.",
            "Things can be broken and made again.", "The world bends and so do I.", "Sounds can carry meaning."};
    public static final String[] BELIEF = {"a stranger", "a friend", "a threat", "a god", "a toy", "a lost echo of itself"};

    public static final class Ctx {
        public boolean water, trees, crystals, builds, spoke;
        public float light, dreamerDist, fear, curio, aggr, social;
        public int neighbors, sleep, state, wood, stone, crystal;
        public String event;
    }

    public final float[] know = new float[N];
    public int dreamerBelief;
    public final List<String> thoughts = new ArrayList<>();
    public final int[] imagery = new int[6];
    public final float[] ix = new float[6], iy = new float[6], ip = new float[6];
    public int imageCount;

    private final float[] cool = new float[N];
    private final boolean[] was = new boolean[N];
    private final String[] names;
    private final Random r;
    private float thinkT = 0.4f;

    public Mind(Random g, String[] names) {
        this.names = names;
        for (int i = 0; i < N; i++) {
            float p = i == BUILD ? 0.5f : i == DESTROY ? 0.3f : 0.55f;
            know[i] = g.nextFloat() < p ? 0.55f + 0.45f * g.nextFloat() : 0.05f + 0.3f * g.nextFloat();
            was[i] = know[i] >= 0.5f;
        }
        dreamerBelief = g.nextInt(BELIEF.length);
        r = new Random(g.nextLong());
        push("I am awake... or something like it.");
    }

    // ------------------------------------------------------------------ editing (called from the mind editor)
    public void teach(int c) {
        know[c] = 1f; cool[c] = 0f; was[c] = true;
        push(pick(new String[]{"A voice inside me says \"" + LABEL[c] + "\". Oh! Now I understand " + ref(c) + ".",
                "Something clicked: " + LABEL[c] + ". " + FACT[c], "It is so obvious now. " + cap(LABEL[c]) + ". How did I ever not know?"}));
    }

    public void forget(int c) {
        know[c] = 0f; cool[c] = 150f; was[c] = false;
        push(pick(new String[]{"Something just left my head. What was it? \"" + names[c] + "\"?",
                "I had a word for something. The word is gone.", "A hole where a thought used to be... \"" + names[c] + "\"?"}));
    }

    public void push(String s) {
        thoughts.add(s);
        while (thoughts.size() > 9) thoughts.remove(0);
    }

    // ------------------------------------------------------------------ simulation
    private void learn(int c, float amt) { if (cool[c] <= 0f) know[c] = Math.min(1f, know[c] + amt); }

    public void update(float dt, Ctx c) {
        for (int i = 0; i < N; i++) if (cool[i] > 0f) cool[i] -= dt;
        if (c.water) learn(WATER, dt * 0.03f);
        if (c.trees) learn(TREES, dt * 0.03f);
        if (c.light > 0.6f) learn(LIGHT, dt * 0.01f); else if (c.light < 0.4f) learn(DARK, dt * 0.012f);
        if (c.dreamerDist < 16f) learn(DREAMER, dt * 0.04f);
        if (c.neighbors > 0) learn(OTHERS, dt * 0.03f);
        learn(MUSIC, dt * 0.004f);
        if (c.crystals) learn(CRYSTALS, dt * 0.03f);
        if (c.builds) learn(BUILD, dt * 0.03f);
        if (c.aggr > 0.7f) learn(DESTROY, dt * 0.01f);
        if (c.sleep > 0) learn(SLEEP, dt * 0.01f);
        if (c.spoke) { learn(SPEECH, 0.05f); c.spoke = false; }
        for (int i = 0; i < N; i++) {
            boolean k = know[i] >= 0.5f;
            if (k != was[i]) {
                was[i] = k;
                if (k) push("I understand something new: " + LABEL[i] + ". " + FACT[i]);
            }
        }
        if (c.event != null) { push(c.event); c.event = null; }
        thinkT -= dt;
        if (thinkT <= 0f) {
            thinkT = 2.4f + r.nextFloat() * 2.6f;
            push(generate(c));
            refresh(c);
            if (r.nextInt(40) == 0) dreamerBelief = r.nextInt(BELIEF.length);
        }
    }

    // ------------------------------------------------------------------ imagery
    private void refresh(Ctx c) {
        imageCount = 0;
        if (c.dreamerDist < 22f) addImage(DREAMER);
        if (c.water) addImage(WATER);
        if (c.trees) addImage(TREES);
        if (c.crystals) addImage(CRYSTALS);
        if (c.builds) addImage(BUILD);
        if (c.neighbors > 0) addImage(OTHERS);
        for (int t = 0; t < 14 && imageCount < 6; t++) addImage(r.nextInt(N));
    }

    private void addImage(int id) {
        if (imageCount >= 6) return;
        for (int i = 0; i < imageCount; i++) if (imagery[i] == id || imagery[i] == -(id + 1)) return;
        imagery[imageCount] = know[id] >= 0.5f ? id : -(id + 1);
        ix[imageCount] = 0.08f + 0.84f * r.nextFloat();
        iy[imageCount] = 0.12f + 0.7f * r.nextFloat();
        ip[imageCount] = r.nextFloat() * 6.28f;
        imageCount++;
    }

    // ------------------------------------------------------------------ thought generation
    private String generate(Ctx c) {
        float roll = r.nextFloat();
        if (c.sleep >= 1 && roll < (c.sleep == 1 ? 0.3f : 0.55f)) return dream(c.sleep);
        if (roll < 0.30f) return situation(c);
        if (roll < 0.42f) return feeling(c);
        if (roll < 0.52f) return pick(DREAMER_LINES[dreamerBelief]);
        return muse();
    }

    private static final String[] OPEN = {"I think", "Maybe", "I remember that", "Something tells me", "I wonder whether",
            "It feels like", "I have heard that", "I dreamed that", "I suspect", "Nobody told me, but", "Strangely,",
            "Deep inside I know", "Sometimes I believe", "I keep wondering if"};
    private static final String[] PRED = {"{0} is older than the sky", "{0} hums when nobody listens", "{0} follows me when I close my eyes",
            "{0} is softer than it looks", "{0} wants to be remembered", "{0} was here before the first step", "{0} tastes like a colour",
            "{0} is listening", "{0} changes when I look away", "{0} has a name I cannot pronounce", "{0} grows in the dark",
            "{0} is bigger on the inside", "{0} belongs to everyone and no one", "{0} is a door", "{0} is slowly forgetting me",
            "{0} dances when the ground is quiet", "{0} remembers my footsteps", "{0} is only pretending to be {1}",
            "{0} and {1} are secretly the same", "{0} is afraid of {1}", "{0} could be made out of {1}", "{0} would be lonely without {1}"};
    private static final String[] S_FLEE = {"Too close! {d} is too close!", "Run, run, run.", "My legs know the way before I do.",
            "I do not like how it looks at me.", "Away, away. Hide in the shape of the hills."};
    private static final String[] S_FOLLOW = {"I want to see what {d} does next.", "It moves so strangely. I must follow.",
            "Maybe it knows where the good things are.", "Stay close. Stay curious."};
    private static final String[] S_GROUP = {"Together is warmer.", "The others hum the same note as me.", "I like the sound of many footsteps.",
            "Being near them makes the world smaller, in a good way."};
    private static final String[] S_GATHER = {"I need materials. Look for the useful ones.", "Gathering. Gathering. Gathering.",
            "This will do nicely for a home.", "Everything is made of something else."};
    private static final String[] S_BUILD = {"Place the base, then the roof.", "It will still stand when I am gone.", "A home! Mine, perhaps.",
            "Stack carefully. The world is watching.", "Making is the opposite of forgetting."};
    private static final String[] S_DESTROY = {"It must fall.", "It was in the way.", "Breaking is also a kind of making.",
            "Some shapes only exist to be knocked over."};
    private static final String[] S_DANCE = {"The music is in my knees.", "Spin, spin, spin.", "I do not move the dance, the dance moves me."};
    private static final String[] S_IDLE = {"Nothing important... except everything.", "I walk because the ground allows it.",
            "Left foot, right foot, wonder.", "There is a lot of sky today.", "Where did all this world come from?"};
    private static final String[] F_FEAR = {"Something is wrong with the light.", "I feel watched by the entire horizon.", "My heart is a small drum.",
            "Everything is a little too close."};
    private static final String[] F_CURIO = {"What is behind that hill? And behind that?", "I want to touch everything once.",
            "Questions are my favourite food.", "Why? Why? Why?"};
    private static final String[] F_AGGR = {"Something in me wants to push things over.", "My hands are restless.", "I feel like a storm in a small body.",
            "I am tired of standing still."};
    private static final String[] F_SOC = {"I miss the sound of someone else's voice.", "We should all be closer.", "A crowd is a kind of blanket.",
            "Is anyone else thinking this?"};
    private static final String[] F_CALM = {"All is quiet. Even my thoughts are quiet.", "The world is the right size today.",
            "I could stay here forever. Or just a moment.", "Breathe in the colours."};
    private static final String[][] DREAMER_LINES = {
            {"Who is it? Where did it come from?", "It does not belong to this place.", "It moves without a body I can find."},
            {"It is nice to have it around.", "I think it likes me.", "When it is near, the world feels safer."},
            {"It is dangerous. I can feel it.", "Keep distance. Always keep distance.", "It looks at me as if I were small."},
            {"It made the world... or maybe it IS the world.", "It watches from outside the dream.", "I should be quiet when it passes."},
            {"I want to poke it.", "If I follow it, does it run?", "Is it a game? I hope it is a game."},
            {"It is me, from another dream.", "It speaks like I do, only silently.", "Why does it walk the way I would walk?"}};
    private static final String[][] DREAM = {
            {},
            {"The ground is floating... or am I?", "Gravity is only a rumour.", "The hills are breathing out.", "Islands drift where the hills used to be.",
                    "Everything leans toward something I cannot see.", "I am lighter than my own shadow."},
            {"Up is down today.", "The sky is melting into my feet.", "I am very tall and very small.", "The world folded itself like paper.",
                    "Everything is the opposite colour of itself.", "Time is a slow river, and I am a stone."},
            {"Colours are speaking. They never stop.", "Everything is a spiral.", "I am many. We are one. Who is we?",
                    "The world has a heartbeat, and it is angry.", "I tasted a sound and it tasted back.", "Ripples, ripples, ripples. Forever."}};

    private String muse() {
        int a = pickConcept(), b = pickConcept();
        if (b == a) b = (a + 1 + r.nextInt(N - 1)) % N;
        if (know[a] < 0.2f && r.nextInt(3) == 0)
            return "I keep noticing something, but I have no word for it. I call it \"" + names[a] + "\".";
        return pick(OPEN) + " " + pick(PRED).replace("{0}", ref(a)).replace("{1}", ref(b)) + ".";
    }

    private String situation(Ctx c) {
        String d = ref(DREAMER);
        if (r.nextInt(4) == 0) {
            if (c.water) return know[WATER] >= 0.5f ? "Water. I should keep my feet dry." : "The ground ends in something blue that moves. I have no word for it.";
            if (c.crystals) return know[CRYSTALS] >= 0.5f ? "A crystal. It hums at me." : "A shiny thing is humming. What is it?";
            if (c.builds) return know[BUILD] >= 0.5f ? "Someone built that. I understand why." : "Someone made that on purpose. Why would anyone do that?";
            if (c.trees) return know[TREES] >= 0.5f ? "Trees. So many tall, patient friends." : "Tall, still things. Are they alive?";
            if (c.wood + c.stone + c.crystal > 0) return "I carry " + c.wood + " wood, " + c.stone + " stone and " + c.crystal + " crystal.";
        }
        String[] set = switch (c.state) {
            case Creature.FLEE -> S_FLEE;
            case Creature.FOLLOW -> S_FOLLOW;
            case Creature.GROUP -> S_GROUP;
            case Creature.GATHER -> S_GATHER;
            case Creature.BUILD -> S_BUILD;
            case Creature.DESTROY -> S_DESTROY;
            case Creature.DANCE -> S_DANCE;
            default -> S_IDLE;
        };
        return pick(set).replace("{d}", d);
    }

    private String feeling(Ctx c) {
        if (c.fear > 0.6f) return pick(F_FEAR);
        if (c.aggr > 0.6f) return pick(F_AGGR);
        if (c.curio > 0.6f) return pick(F_CURIO);
        if (c.social > 0.6f) return pick(F_SOC);
        return pick(F_CALM);
    }

    private String dream(int sleep) { return pick(DREAM[Math.max(1, Math.min(3, sleep))]); }

    private int pickConcept() {
        int id = r.nextInt(N);
        for (int t = 0; t < 3 && know[id] < 0.5f; t++) id = r.nextInt(N);
        return id;
    }

    /** How the creature refers to an idea: its own word if it understands it, a vague name otherwise. */
    public String ref(int c) {
        return know[c] >= 0.5f ? LABEL[c] + " (\"" + names[c] + "\")" : "that thing I call \"" + names[c] + "\"";
    }

    public String nameOf(int c) { return names[c]; }

    private String pick(String[] a) { return a[r.nextInt(a.length)]; }
    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
