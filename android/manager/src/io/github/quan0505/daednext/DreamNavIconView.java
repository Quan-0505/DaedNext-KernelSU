package io.github.quan0505.daednext;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Static opaline navigation art. No bitmap allocation or idle animation. */
final class DreamNavIconView extends View {
    private final int kind;
    private final boolean selected,light;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int violet,cyan,rim;
    DreamNavIconView(Context c,int kind,boolean selected,boolean light){
        super(c);this.kind=kind;this.selected=selected;this.light=light;
        violet=selected?(light?0xff8b70d7:0xffb49aff):(light?0xff9891b1:0xff9994b6);
        cyan=selected?(light?0xff9de0ef:0xff83d8ef):(light?0xffbfd7df:0xffa3bbc9);
        rim=light?0xff75618f:0xffd9d1f2;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void fill(Shader shader){p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);p.setShader(shader);}
    private LinearGradient gradient(float x1,float y1,float x2,float y2,int a,int b){return new LinearGradient(x1,y1,x2,y2,a,b,Shader.TileMode.CLAMP);}
    private void glass(Canvas c,Path path){
        fill(gradient(3,3,25,25,cyan,violet));c.drawPath(path,p);
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.75f);p.setColor(rim);p.setAlpha(selected?180:145);c.drawPath(path,p);
    }
    private void star(Canvas c,float x,float y,float size){
        Path s=new Path();s.moveTo(x,y-size);s.quadTo(x+.25f*size,y-.25f*size,x+size,y);s.quadTo(x+.25f*size,y+.25f*size,x,y+size);s.quadTo(x-.25f*size,y+.25f*size,x-size,y);s.quadTo(x-.25f*size,y-.25f*size,x,y-size);
        fill(null);p.setColor(light?0xffa18cc8:0xffd2c7ff);p.setAlpha(selected?220:105);c.drawPath(s,p);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();c.scale(getWidth()/52f,getHeight()/38f);
        if(selected){
            fill(new RadialGradient(26,20,23,new int[]{light?0x428f7be0:0x478e7dd9,0x008f7be0},null,Shader.TileMode.CLAMP));c.drawOval(1,0,51,38,p);
            fill(gradient(8,3,45,35,light?0xffefebfa:0xff39344f,light?0xffe3edf4:0xff263748));c.drawRoundRect(5,3,47,35,13,13,p);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.6f);p.setColor(light?0xddffffff:0x668f9abf);c.drawRoundRect(5.4f,3.4f,46.6f,34.6f,13,13,p);
        }
        c.translate(12,5);Path path=new Path();
        if(kind==0){
            path.moveTo(4,12);path.lineTo(13,4.5f);path.quadTo(14,3.6f,15,4.5f);path.lineTo(24,12);path.lineTo(22,13);path.lineTo(22,22);path.quadTo(22,24,20,24);path.lineTo(8,24);path.quadTo(6,24,6,22);path.lineTo(6,13);path.close();glass(c,path);
            fill(gradient(11,16,17,24,light?0xfff6f2ff:0xff3f3b5c,light?0xffddd8ed:0xff222a3d));c.drawRoundRect(11,16,17,24,2,2,p);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.8f);p.setColor(light?0xccffffff:0x99f4eaff);c.drawLine(8,12,14,7,p);
        }else if(kind==8){
            for(int row=0;row<2;row++)for(int col=0;col<2;col++){
                float x=4+col*12,y=4+row*12;c.save();c.rotate((row==col?-5:5),x+4,y+4);path.reset();path.addRoundRect(x,y,x+8,y+8,2.5f,2.5f,Path.Direction.CW);glass(c,path);
                p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.7f);p.setColor(light?0xe6ffffff:0xaaffffff);c.drawLine(x+2,y+1.8f,x+5.3f,y+1.8f,p);c.restore();
            }
        }else if(kind==3){
            fill(null);p.setColor(cyan);p.setAlpha(selected?115:65);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.7f);c.save();c.rotate(-27,14,15);c.drawOval(2,7,26,22,p);c.restore();
            p.setShader(gradient(3,6,25,24,cyan,violet));p.setAlpha(255);p.setStrokeWidth(2.7f);p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(3,5,25,27,225,90,false,p);c.drawArc(8,12,20,25,225,90,false,p);
            fill(gradient(12,20,16,25,cyan,violet));c.drawCircle(14,22,2.5f,p);
        }else{
            for(int i=0;i<64;i++){double a=i*Math.PI/32;float r=9.1f+1.35f*(float)Math.cos(a*8);float x=14+r*(float)Math.cos(a),y=14+r*(float)Math.sin(a);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();glass(c,path);
            fill(gradient(10,10,18,18,light?0xfff8f5ff:0xff4c456c,light?0xffd2e5ed:0xff243c4b));c.drawCircle(14,14,4.6f,p);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(rim);p.setAlpha(180);c.drawCircle(14,14,4.6f,p);
            fill(null);p.setColor(light?0xddffffff:0xcce6f4ff);c.drawCircle(12.7f,12.6f,1.1f,p);
        }
        star(c,25,3.5f,selected?2:1.4f);if(selected)star(c,2,24,1.2f);
        c.restore();
    }
}
