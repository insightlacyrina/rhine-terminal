import dev.rhine.terminal.SoftTapDetector;

/** Portable synthetic traces, not a substitute for a physical tap test. */
public final class SoftTapDetectorTest {
    private static long now;
    private static int hits;
    private static void step(SoftTapDetector d,float x,float y,float z,int ms) {
        now+=ms*1_000_000L;
        if(d.sample(now,x,y,z,() -> true)) hits++;
    }
    private static void rest(SoftTapDetector d,float x,float y,float z,int interval,int ms) {
        for(int t=0;t<ms;t+=interval) step(d,x,y,z,interval);
    }
    private static void expect(boolean ok,String msg) { if(!ok) throw new AssertionError(msg); }
    private static void trace(String name,float gx,float gy,float gz,int interval) {
        now=1_000_000_000L; hits=0;
        SoftTapDetector d=new SoftTapDetector(); rest(d,gx,gy,gz,interval,400);
        step(d,gx,gy,gz+.72f,interval); step(d,gx,gy,gz,interval);
        expect(hits==1,name+" light tap");
        rest(d,gx,gy,gz,interval,200);
        step(d,gx,gy,gz+.72f,interval); step(d,gx,gy,gz,interval);
        expect(hits==1,name+" cooldown");
        rest(d,gx,gy,gz,interval,1000);
        step(d,gx+2.6f,gy,gz+.5f,interval); step(d,gx,gy,gz,interval);
        expect(hits==1,name+" lateral jostle");
        rest(d,gx,gy,gz,interval,1000);
        step(d,gx,gy,gz+6.5f,interval); step(d,gx,gy,gz,interval);
        expect(hits==1,name+" broad impact");
        rest(d,gx,gy,gz,interval,1000);
        step(d,gx,gy,gz+1f,interval);
        for(int t=0;t<300;t+=interval) step(d,gx,gy,gz+1f,interval);
        step(d,gx,gy,gz,interval);
        expect(hits==1,name+" sustained motion");
        rest(d,gx,gy,gz,interval,1000);
        expect(d.acceptDirect(now),name+" direct accepted");
        rest(d,gx,gy,gz,interval,200);
        step(d,gx,gy,gz+.72f,interval); step(d,gx,gy,gz,interval);
        expect(hits==1,name+" direct/sensor dedup");
        rest(d,gx,gy,gz,interval,1000);
        step(d,gx,gy,gz+.72f,interval); step(d,gx,gy,gz,interval);
        expect(hits==2,name+" rearmed");
    }
    public static void main(String[] args) {
        for(int interval:new int[]{10,20,40,80}) {
            trace("flat/"+interval,0,0,9.8f,interval);
            trace("portrait/"+interval,0,9.6f,1.9f,interval);
            trace("landscape/"+interval,9.6f,0,1.9f,interval);
        }
        SoftTapDetector detector = new SoftTapDetector();
        now = 1_000_000_000L;
        rest(detector, 0, 0, 9.8f, 10, 400);
        detector.sample(now += 10_000_000L, 0, 0, 10.52f, () -> false);
        expect(!detector.sample(now += 10_000_000L, 0, 0, 9.8f, () -> false), "unarmed tap");
        expect(detector.acceptDirect(now), "unarmed tap leaves direct touch available");
        System.out.println("Soft tap synthetic orientation and sampling traces passed");
    }
}
