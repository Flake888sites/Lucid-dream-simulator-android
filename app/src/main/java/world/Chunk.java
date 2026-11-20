package world;

import java.util.ArrayList;

public final class Chunk {
    public static final int TREE = 0, ROCK = 1, CRYSTAL = 2, STRUCT = 3;
    public record Spawn(float x, float z, long seed) {}
    /** A harvestable / breakable object that is baked into the chunk mesh. id is unique per map cell. */
    public record Obj(int kind, float x, float y, float z, float size, long id) {}

    public final int cx, cz;
    public Mesh mesh = new Mesh();
    public int trees, rocks, crystals, structs, water;
    public World owner;
    public final ArrayList<Spawn> spawns = new ArrayList<>();
    public final ArrayList<Obj> objs = new ArrayList<>();

    public Chunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
}
