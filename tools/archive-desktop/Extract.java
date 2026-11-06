import dev.rhine.terminal.LightHomeScene;
import dev.rhine.terminal.LockBlackoutScene;
import dev.rhine.terminal.LockComposition;
import dev.rhine.terminal.LockSnapshotScene;
import dev.rhine.terminal.SculptureScene;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public class Extract {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.writeString(directory.resolve("instances.txt"), Integer.toString(SculptureScene.FIN_COUNT));
        Files.writeString(directory.resolve("blackout.glsl"), LockBlackoutScene.FRAGMENT_SHADER);
        Files.writeString(directory.resolve("vertex.glsl"), SculptureScene.VERTEX_SHADER);
        Files.writeString(directory.resolve("fragment.glsl"), SculptureScene.FRAGMENT_SHADER);
        Files.writeString(directory.resolve("stage-vertex.glsl"), LockComposition.VERTEX);
        Files.writeString(directory.resolve("stage-fragment.glsl"), LockComposition.STAGE);
        Files.writeString(directory.resolve("hud-fragment.glsl"), LockComposition.HUD);
        Files.writeString(directory.resolve("home-vertex.glsl"), LightHomeScene.VERTEX_SHADER);
        Files.writeString(directory.resolve("home-fragment.glsl"), LightHomeScene.FRAGMENT_SHADER);
        Files.writeString(directory.resolve("snapshot-vertex.glsl"), LockSnapshotScene.VERTEX_SHADER);
        Files.writeString(directory.resolve("snapshot-fragment.glsl"), LockSnapshotScene.FRAGMENT_SHADER);

        SculptureScene.Mesh mesh = SculptureScene.createMesh();
        int vertexCount = mesh.vertices.remaining() * Float.BYTES / SculptureScene.STRIDE_BYTES;
        ByteBuffer vertices = ByteBuffer.allocate(mesh.vertices.remaining() * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        while (mesh.vertices.hasRemaining()) {
            float value = mesh.vertices.get();
            if (!Float.isFinite(value)) throw new IllegalStateException("Invalid vertex");
            vertices.putFloat(value);
        }
        ByteBuffer indices = ByteBuffer.allocate(mesh.indexCount * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        while (mesh.indices.hasRemaining()) {
            int index = mesh.indices.get();
            if (index < 0 || index >= vertexCount) throw new IllegalStateException("Mesh index out of range");
            indices.putInt(index);
        }
        if (indices.hasRemaining()) throw new IllegalStateException("Index count mismatch");
        Files.write(directory.resolve("vertices.bin"), vertices.array());
        Files.write(directory.resolve("indices.bin"), indices.array());
    }
}
