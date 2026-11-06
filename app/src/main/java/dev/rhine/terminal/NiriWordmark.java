package dev.rhine.terminal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Vector counterpart of assets/niri-wordmark.svg, in a 200 by 76 coordinate space. */
final class NiriWordmark {
    private static Path polygon(float... xy) {
        Path p = new Path(); p.moveTo(xy[0], xy[1]);
        for (int i=2; i<xy.length; i+=2) p.lineTo(xy[i], xy[i+1]);
        p.close(); return p;
    }
    static void draw(Canvas c, float left, float top, float width, int color, int background) {
        c.save(); c.translate(left, top); c.scale(width/200f, width/200f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        c.drawPath(polygon(0,0,12,0,53,50,53,0,66,0,66,76,54,76,13,25,13,76,0,76),p);
        c.drawRect(82,0,95,76,p);
        Path r = new Path(); r.setFillType(Path.FillType.EVEN_ODD);
        r.moveTo(111,0); r.lineTo(143,0); r.cubicTo(163,0,173,10,173,26); r.cubicTo(173,38,167,47,156,50); r.lineTo(176,76); r.lineTo(160,76); r.lineTo(142,52); r.lineTo(124,52); r.lineTo(124,76); r.lineTo(111,76); r.close();
        r.moveTo(124,12); r.lineTo(124,40); r.lineTo(142,40); r.cubicTo(154,40,160,35,160,26); r.cubicTo(160,17,154,12,142,12); r.close(); c.drawPath(r,p);
        c.drawRect(187,0,200,76,p);
        p.setColor(background); c.drawCircle(138,53,27,p);
        p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4.5f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        Path loop = new Path(); loop.moveTo(116,54); loop.cubicTo(116,46,121,42,127,42); loop.cubicTo(134,42,138,48,142,52); loop.cubicTo(146,57,150,62,157,62); loop.cubicTo(163,62,168,58,168,51); loop.cubicTo(168,44,164,42,158,42); loop.cubicTo(153,42,150,45,147,48); loop.moveTo(168,51); loop.cubicTo(168,58,164,62,158,62); loop.cubicTo(151,62,147,57,143,53); loop.cubicTo(139,48,135,42,128,42); loop.cubicTo(121,42,116,47,116,54); loop.cubicTo(116,61,121,64,127,64); loop.cubicTo(133,64,137,60,141,56); c.drawPath(loop,p);
        p.setStrokeWidth(3.5f); p.setStrokeCap(Paint.Cap.SQUARE); c.drawLine(123,53,131,53,p); c.drawLine(127,49,127,57,p); c.drawLine(154,53,161,53,p);
        c.restore();
    }
}
