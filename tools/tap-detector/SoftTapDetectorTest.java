import dev.rhine.terminal.SoftTapDetector;

/** Run with javac and java; synthetic traces do not establish on-device sensitivity. */
public final class SoftTapDetectorTest {
    private static long ns=1_000_000_000L;
    private static int hits;
    private static void sample(SoftTapDetector d, float x, float y, float z, int ms) {
        ns+=ms*1_000_000L;
        if(d.sample(ns,x,y,z)) hits++;
    }
    private static void quiet(SoftTapDetector d, int n) { for(int i=0;i<n;i++) sample(d,0,0,9.8f,20); }
    private static void expect(boolean value, String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        SoftTapDetector d=new SoftTapDetector(); quiet(d,15);
        sample(d,0,0,12,20); sample(d,0,0,9.8f,20);
        expect(hits==1,"short normal-axis impulse should trigger");
        quiet(d,10); sample(d,0,0,12,20); sample(d,0,0,9.8f,20);
        expect(hits==1,"cooldown must suppress repeated impulse");
        quiet(d,50); sample(d,4,0,10,20); sample(d,0,0,9.8f,20);
        expect(hits==1,"lateral movement must not trigger");
        quiet(d,30); sample(d,0,0,17,20); sample(d,0,0,9.8f,20);
        expect(hits==1,"large motion must not trigger");
        quiet(d,50); expect(d.acceptDirect(ns),"direct event accepted after cooldown");
        quiet(d,10); sample(d,0,0,12,20); sample(d,0,0,9.8f,20);
        expect(hits==1,"direct event and sensor must deduplicate");
        quiet(d,50); sample(d,0,0,12,20); sample(d,0,0,9.8f,20);
        expect(hits==2,"another tap after cooldown should trigger");
        System.out.println("Soft tap synthetic traces passed");
    }
}
