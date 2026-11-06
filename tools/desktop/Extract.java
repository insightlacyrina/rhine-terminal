import dev.rhine.terminal.SculptureScene;
import dev.rhine.terminal.WarningTimeline;
import java.nio.*; import java.nio.file.*;
public class Extract {
  public static void main(String[] args) throws Exception {
    Path dir=Paths.get(args[0]); Files.createDirectories(dir);
    Files.write(dir.resolve("vertex.glsl"),SculptureScene.VERTEX_SHADER.getBytes("UTF-8"));
    Files.write(dir.resolve("fragment.glsl"),SculptureScene.FRAGMENT_SHADER.getBytes("UTF-8"));
    Files.write(dir.resolve("plane-vertex.glsl"),SculptureScene.PLANE_VERTEX_SHADER.getBytes("UTF-8"));
    Files.write(dir.resolve("plane-fragment.glsl"),SculptureScene.PLANE_FRAGMENT_SHADER.getBytes("UTF-8"));
    SculptureScene.Mesh mesh=SculptureScene.createMesh();
    ByteBuffer vb=ByteBuffer.allocate(mesh.vertices.remaining()*4).order(ByteOrder.LITTLE_ENDIAN);
    int vertices=mesh.vertices.remaining()*Float.BYTES/SculptureScene.STRIDE_BYTES;
    while(mesh.vertices.hasRemaining()) {
      float value=mesh.vertices.get();
      if(!Float.isFinite(value)) throw new AssertionError("Invalid vertex");
      vb.putFloat(value);
    }
    ByteBuffer ib=ByteBuffer.allocate(mesh.indices.remaining()*4).order(ByteOrder.LITTLE_ENDIAN);
    while(mesh.indices.hasRemaining()) {
      int index=mesh.indices.get();
      if(index<0||index>=vertices) throw new AssertionError("Mesh index out of range");
      ib.putInt(index);
    }
    Files.write(dir.resolve("vertices.bin"),vb.array()); Files.write(dir.resolve("indices.bin"),ib.array());
    Files.writeString(dir.resolve("instances.txt"), Integer.toString(SculptureScene.FIN_COUNT));
    if(WarningTimeline.intensity(-1)!=0||WarningTimeline.intensity(0)!=0||WarningTimeline.intensity(.20f)!=1
        ||WarningTimeline.intensity(2.60f)!=1||WarningTimeline.intensity(3f)<=0
        ||WarningTimeline.intensity(3.3f)!=0) throw new AssertionError("Warning must enter, hold and recover");
    Files.writeString(dir.resolve("timeline.json"),"{\"enter\":"+WarningTimeline.ENTER_SECONDS
        +",\"hold\":"+WarningTimeline.HOLD_UNTIL_SECONDS+",\"end\":"+WarningTimeline.END_SECONDS+"}");
    System.out.println("Desktop mesh and warning checks passed; triangles="+mesh.indexCount/3*SculptureScene.FIN_COUNT);
  }
}
