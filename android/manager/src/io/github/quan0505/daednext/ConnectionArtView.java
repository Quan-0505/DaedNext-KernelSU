package io.github.quan0505.daednext;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original procedural artwork. Static rendering avoids a background animation loop. */
final class ConnectionArtView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean enabled;
    private final boolean bypass;
    private final boolean light;

    ConnectionArtView(Context context, boolean enabled, boolean bypass, boolean light) {
        super(context); this.enabled = enabled; this.bypass = bypass; this.light = light;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x=getWidth()*.5f, y=getHeight()*.49f;
        float radius=Math.min(getWidth()*.215f,getHeight()*.35f);
        int blue=enabled?0xff4f7cff:0xff647087;
        int violet=enabled?0xffac8cff:0xff8a91a3;
        int mint=enabled?(bypass?0xffa1e8df:0xff81d9ff):0xffa2aebe;

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(new RadialGradient(x,y,radius*2.25f,
            new int[]{light?0x387e82cf:(enabled?0x454259c5:0x183c4660),light?0x147c92d9:0x132f3a8c,0x00000000},
            new float[]{0,.5f,1},Shader.TileMode.CLAMP));
        canvas.drawCircle(x,y,radius*2.25f,paint);
        paint.setShader(null);

        // Sparse fixed stars and a shallow orbital arc frame the connection core.
        for(int i=0;i<24;i++) {
            double a=i*2.39996323;
            float distance=radius*(1.35f+(i%7)*.13f);
            paint.setColor(light?(i%4==0?0x806c68a5:0x406e719e):(i%4==0?0x99b7c3ff:0x375f709e));
            canvas.drawCircle(x+(float)Math.cos(a)*distance*1.35f,
                y+(float)Math.sin(a)*distance*.73f, i%4==0?1.6f:1f,paint);
        }
        canvas.save();canvas.rotate(-21,x,y);
        paint.setAlpha(255);
        RectF orbit=new RectF(x-radius*1.8f,y-radius*.65f,x+radius*1.8f,y+radius*.65f);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f);
        paint.setShader(new LinearGradient(orbit.left,y,orbit.right,y,
            new int[]{0x005e72b8,0x667f91df,0x00a9b5ff},null,Shader.TileMode.CLAMP));
        canvas.drawArc(orbit,185,160,false,paint);
        paint.setShader(null);canvas.restore();

        // An asymmetric glass-like core, built entirely from native vector paths.
        Path core=new Path();
        core.moveTo(x-radius*.03f,y-radius);
        core.cubicTo(x+radius*.84f,y-radius*1.05f,x+radius*1.15f,y-radius*.3f,x+radius*.93f,y+radius*.25f);
        core.cubicTo(x+radius*.73f,y+radius*.93f,x+radius*.1f,y+radius*1.13f,x-radius*.5f,y+radius*.79f);
        core.cubicTo(x-radius*1.08f,y+radius*.42f,x-radius*1.05f,y-radius*.24f,x-radius*.69f,y-radius*.69f);
        core.cubicTo(x-radius*.49f,y-radius*.94f,x-radius*.25f,y-radius*1.03f,x-radius*.03f,y-radius);
        core.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(x-radius,y-radius,x+radius,y+radius,
            new int[]{mint,blue,0xff5854b7,violet,0xff333a72},
            new float[]{0,.32f,.55f,.8f,1},Shader.TileMode.CLAMP));
        canvas.drawPath(core,paint);paint.setShader(null);
        canvas.save();canvas.clipPath(core);
        paint.setShader(new RadialGradient(x-radius*.43f,y-radius*.47f,radius*1.25f,
            new int[]{0xc8dcffff,0x22469efa,0x00000000},new float[]{0,.6f,1},Shader.TileMode.CLAMP));
        canvas.drawCircle(x,y,radius*1.6f,paint);paint.setShader(null);
        for(int i=0;i<9;i++) {
            float phase=i*.15f;
            Path strand=new Path();strand.moveTo(x-radius*1.2f,y-radius*.65f+radius*phase);
            strand.cubicTo(x+radius*.8f,y-radius*1.15f+radius*phase,
                x-radius*.8f,y+radius*.9f+radius*phase,x+radius*1.1f,y+radius*.1f+radius*phase);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(radius*(i%3==0?.07f:.025f));
            paint.setColor(i%3==0?0x7195efff:0x807b88e8);canvas.drawPath(strand,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(x+radius*.2f,y+radius*.2f,radius*.78f,
            new int[]{0xc8191c49,0x30182664,0x00000000},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(x+radius*.2f,y+radius*.2f,radius*.78f,paint);paint.setShader(null);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.1f);paint.setColor(0x85c1d4ff);canvas.drawPath(core,paint);

        // A small foreground orbit adds depth without copying reference artwork.
        canvas.save();canvas.rotate(-21,x,y);
        paint.setShader(new LinearGradient(orbit.left,y,orbit.right,y,
            light?new int[]{0x007066ac,0xb07875b3,0x008ea4cf}:new int[]{0x009fb6f9,0xc0bddaff,0x0093aaff},null,Shader.TileMode.CLAMP));
        canvas.drawArc(orbit,12,140,false,paint);paint.setShader(null);canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }
}
