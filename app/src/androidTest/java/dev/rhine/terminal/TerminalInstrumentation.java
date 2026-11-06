package dev.rhine.terminal;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;

/** Dependency-free device checks, with PNG captures available from external app files. */
public final class TerminalInstrumentation extends Instrumentation {
    private boolean skipPreview;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); skipPreview=arguments!=null&&"true".equals(arguments.getString("skipPreview")); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            testArchiveTimeline();
            testInstancedFinMesh();
            testRenderPortraitAndLandscape();
            testSettingsAndComponentRegistration();
            if(!skipPreview) testPreviewActivityLifecycle();
            logDeviceDiagnostics();
            result.putString("stream", "\nOK: archive timeline, 400-fin unit mesh, Canvas portrait/landscape, settings/components, " + (skipPreview?"preview skipped (locked-device capture run)":"preview lifecycle") + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
            result.putString("shortMsg", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void testArchiveTimeline() {
        ArchiveTimeline.FrameState frame = new ArchiveTimeline.FrameState();
        int[][] cases = {
                {0, ArchiveTimeline.FRAME, 0}, {560, ArchiveTimeline.PANELS, 0},
                {1350, ArchiveTimeline.IDLE, 0}, {3100, ArchiveTimeline.SCAN, 0},
                {5099, ArchiveTimeline.SCAN, 0}, {5100, ArchiveTimeline.DENIED, 1},
                {7699, ArchiveTimeline.DENIED, 1}, {7700, ArchiveTimeline.IDLE, 0},
                {10450, ArchiveTimeline.DENIED, 2}, {12449, ArchiveTimeline.DENIED, 2},
                {12450, ArchiveTimeline.IDLE, 0}, {15250, ArchiveTimeline.EXIT, 0},
                {16650, ArchiveTimeline.HOME, 0}, {18200, ArchiveTimeline.IDLE, 0}
        };
        for (int[] item : cases) {
            frame.reset();
            ArchiveTimeline.demo(frame,item[0]);
            check(frame.stage == item[1] && frame.intensity == item[2] && !frame.nativeFailure,
                    "Visual phase at "+item[0]+" ms");
        }
        frame.reset();
        ArchiveTimeline.demo(frame,738);
        check(frame.snapshot > .99f && frame.snapshotTravel > .85f && frame.snapshotCorners > 0,
                "Entry card travels while its corners fade");
        frame.reset();
        ArchiveTimeline.demo(frame,3720);
        check(Math.abs(frame.scan - .92f * (1 - (float)Math.exp(-1.4f * .62f))) < .001f
                        && frame.scanOpacity > .97f,
                "Verification approaches the upstream progress limit exponentially");
        frame.reset();
        ArchiveTimeline.demo(frame,5100);
        check(frame.denyMix == 0, "Denial begins at the palette transition origin");
        ArchiveTimeline.recover(frame, 0);
        check(frame.denyMix == 1 && Math.abs(frame.recoveryGlitch - .45f) < .001f,
                "Recovery starts from the denial palette with the upstream glitch");
        ArchiveTimeline.recover(frame, 300);
        check(frame.denyMix < .001f && frame.recoveryGlitch < .45f,
                "Recovery palette and glitch decay after the burst");
        float slow = ArchiveTimeline.motionTime(6.2) - ArchiveTimeline.motionTime(6);
        float fast = ArchiveTimeline.motionTime(.2) - ArchiveTimeline.motionTime(0);
        check(Math.abs(fast-slow) > .05f && ArchiveTimeline.motionTime(24) == 0,
                "Idle accelerates and decelerates around the same cycle");
        frame.reset();
        ArchiveTimeline.demo(frame,16100);
        check(frame.peel > .6f && frame.snapshot > .99f && frame.snapshotTravel < .7f,
                "Peel exposes the returning Home card");
    }

    private static void testInstancedFinMesh() {
        SculptureScene.Mesh mesh = SculptureScene.createMesh();
        int stride = SculptureScene.STRIDE_BYTES / Float.BYTES;
        check(SculptureScene.FIN_COUNT == 400, "Upstream fin count");
        check(mesh.indexCount == 36 && mesh.indices.remaining() == 36, "Twelve triangles in the unit box");
        check(mesh.vertices.remaining() == 24 * stride, "Four vertices with independent normals on each face");
        for (int face = 0; face < 6; face++) {
            int first = face * 4 * stride;
            int axis = face / 2;
            float sign = face % 2 == 0 ? 1f : -1f;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = first + vertex * stride;
                check(mesh.vertices.get(offset + axis) == sign * .5f, "Unit box face position");
                check(mesh.vertices.get(offset + 3 + axis) == sign, "Outward face normal");
            }
        }
    }

    private void testRenderPortraitAndLandscape() throws Exception {
        Context context = getTargetContext();
        File dir = new File(context.getExternalFilesDir(null),"screenshots");
        check(dir.isDirectory() || dir.mkdirs(),"Create screenshot directory");
        ArchiveFallback fallback = new ArchiveFallback();
        ArchiveTimeline.FrameState frame = new ArchiveTimeline.FrameState();
        for (boolean portrait : new boolean[]{false,true}) {
            int w=portrait?1600:2560, h=portrait?2560:1600;
            String name=portrait?"portrait":"landscape";
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            try {
                frame.reset();
                fallback.draw(canvas,frame,73,true,true,true,LockDisplayName.DEFAULT);
                check(bitmap.getPixel(10,10)==0xffe4dfdc,"Idle paper");
                int cardX=(int)(w*(portrait?.21f:.18f)), cardY=(int)(h*(portrait?.39f:.35f));
                check((bitmap.getPixel(cardX,cardY)&0xff)<30,"Locked NIRI foreground");
                int railX=(int)(w*(portrait?.25f:.223f));
                check((bitmap.getPixel(railX,2)&0xff)<160
                                && (bitmap.getPixel(railX,h-3)&0xff)<160,
                        "NIRI rail reaches both screen edges");
                writeBitmap(bitmap,dir,name+"-fallback.png");
                fallback.draw(canvas,frame,73,true,false,true,LockDisplayName.DEFAULT);
                check((bitmap.getPixel(cardX,cardY)&0xff)>90,"Unlocked surface omits lock prompt");
                frame.deny(2,false,.5f);
                fallback.draw(canvas,frame,73,true,true,true,LockDisplayName.DEFAULT);
                check(bitmap.getPixel(10,10)==0xff0e0c0d,"Deny hard-cuts to dark stage");
                writeBitmap(bitmap,dir,name+"-fallback-denied.png");
                ArchiveFallback.drawHome(canvas,new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),true,false);
                check(bitmap.getPixel(10,10)==0xffe4dfdc,"Light Home defaults to warm paper");
                writeBitmap(bitmap,dir,name+"-fallback-home.png");
                ArchiveFallback.drawHome(canvas,new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),false,true);
                check(bitmap.getPixel(10,10)==0xff0b0b0c,"Dark gallery uses upstream graphite");
            } finally { bitmap.recycle(); }
        }
    }

    private static void writeBitmap(Bitmap bitmap, File dir, String name) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(new File(dir,name))) {
            check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream),"Write "+name);
        }
    }
    private void testSettingsAndComponentRegistration() throws Exception {
        Context context = getTargetContext();
        check("android.permission.BIND_WALLPAPER".equals(context.getPackageManager().getServiceInfo(new ComponentName(context, RhineWallpaperService.class), PackageManager.GET_META_DATA).permission), "Wallpaper bind permission");
        check("android.permission.BIND_WALLPAPER".equals(context.getPackageManager().getServiceInfo(new ComponentName(context, HomeWallpaperService.class), PackageManager.GET_META_DATA).permission), "Home bind permission");
        check(context.getPackageManager().resolveActivity(new Intent(context, MainActivity.class), 0) != null, "Main activity registered");
        android.content.SharedPreferences prefs = context.getSharedPreferences("terminal-test", 0);
        check(LockDisplayName.DEFAULT.equals(LockDisplayName.read(prefs)), "Default lock name");
        check(prefs.edit().putString(LockDisplayName.KEY, "  测试　ORION  ").commit(), "Persist lock name");
        check("测试 ORION".equals(LockDisplayName.read(prefs)), "Read and normalize the persisted lock name");
        check(LockDisplayName.DEFAULT.equals(LockDisplayName.normalize(" \t　")), "Blank lock name falls back");
        prefs.edit().clear().commit();
        check("android.service.wallpaper.CHANGE_LIVE_WALLPAPER".equals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER), "Wallpaper picker action");
    }
    private void logDeviceDiagnostics() {
        Context context = getTargetContext();
        android.app.KeyguardManager keyguard = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        android.hardware.SensorManager sensors = (android.hardware.SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        android.util.Log.i("RhineDiagnostics", "deviceSecure=" + keyguard.isDeviceSecure() + " keyguardSecure=" + keyguard.isKeyguardSecure() + " keyguardLocked=" + keyguard.isKeyguardLocked() + " rotationVectorAvailable=" + (sensors != null && sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) != null));
    }
    private void testPreviewActivityLifecycle() {
        Intent intent = new Intent(getTargetContext(), PreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (boolean home : new boolean[]{false,true}) {
            intent.putExtra(PreviewActivity.EXTRA_HOME,home);
            Activity activity=startActivitySync(intent);
            waitForIdleSync();
            check(!activity.isFinishing(),"Preview launched");
            runOnMainSync(activity::finish);
            waitForIdleSync();
        }
    }
}
