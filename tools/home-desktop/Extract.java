import dev.rhine.terminal.HomeBackgroundScene;
import dev.rhine.terminal.HomeSculptureScene;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public class Extract {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.writeString(output.resolve("vertex.glsl"), HomeSculptureScene.VERTEX_SHADER);
        Files.writeString(output.resolve("fragment.glsl"), HomeSculptureScene.FRAGMENT_SHADER);
        Files.writeString(output.resolve("background-vertex.glsl"), HomeBackgroundScene.VERTEX_SHADER);
        Files.writeString(output.resolve("background-fragment.glsl"), HomeBackgroundScene.FRAGMENT_SHADER);

        HomeSculptureScene.Mesh mesh = HomeSculptureScene.createMesh();
        int vertexCount = mesh.vertices.remaining() * Float.BYTES / HomeSculptureScene.STRIDE_BYTES;
        ByteBuffer vertices = ByteBuffer.allocate(mesh.vertices.remaining() * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        while (mesh.vertices.hasRemaining()) {
            float value = mesh.vertices.get();
            if (!Float.isFinite(value)) throw new IllegalStateException("Non-finite mesh vertex");
            vertices.putFloat(value);
        }
        ByteBuffer indices = ByteBuffer.allocate(mesh.indices.remaining() * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        while (mesh.indices.hasRemaining()) {
            int index = mesh.indices.get();
            if (index < 0 || index >= vertexCount) throw new IllegalStateException("Mesh index out of range");
            indices.putInt(index);
        }
        if (indices.position() != mesh.indexCount * Integer.BYTES) throw new IllegalStateException("Index count mismatch");
        Files.write(output.resolve("vertices.bin"), vertices.array());
        Files.write(output.resolve("indices.bin"), indices.array());
    }
}
