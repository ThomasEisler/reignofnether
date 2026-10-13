package com.solegendary.reignofnether.rampart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The shape of each rampart segment (its columns), keyed by the segment's origin. A segment's blocks are generated
// from it on both sides, so the server saves the shapes with the world and sends them to players before the
// segments themselves (see RampartShapeClientboundPacket).
public class RampartShapes {

    // a column of wall standing on the ground block at x, y, z, `height` blocks tall
    public record Column(int x, int y, int z, int height) { }

    private static final Map<Long, List<Column>> serverShapes = new HashMap<>();
    private static final Map<Long, List<Column>> clientShapes = new HashMap<>();
    private static boolean serverLoaded = false;

    @Nullable
    public static List<Column> get(Level level, BlockPos origin) {
        if (level.isClientSide())
            return clientShapes.get(origin.asLong());
        ensureLoaded(level);
        return serverShapes.get(origin.asLong());
    }

    public static void put(ServerLevel level, BlockPos origin, List<Column> columns) {
        ensureLoaded(level);
        serverShapes.put(origin.asLong(), List.copyOf(columns));
        save(level);
        RampartShapeClientboundPacket.send(null, origin, columns);
    }

    public static void setClientShape(BlockPos origin, List<Column> columns) {
        clientShapes.put(origin.asLong(), List.copyOf(columns));
    }

    public static Map<Long, List<Column>> getServerShapes() {
        return serverShapes;
    }

    private static void ensureLoaded(Level level) {
        if (serverLoaded || level.getServer() == null)
            return;
        serverLoaded = true;
        serverShapes.clear();
        serverShapes.putAll(RampartSaveData.getInstance(level).shapes);
    }

    static void save(Level level) {
        RampartSaveData data = RampartSaveData.getInstance(level);
        data.shapes.clear();
        data.shapes.putAll(serverShapes);
        data.save();
    }

    static void onServerStopped() {
        serverShapes.clear();
        serverLoaded = false;
    }

    public static void clearClient() {
        clientShapes.clear();
    }
}
