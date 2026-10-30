package io.github.quan0505.daednext;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.PathInterpolator;

/** B concept: native glass globe and dae-inspired goose, with finite switch motion. */
final class GlassGooseView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean light;
    private final Bitmap core;
    private final Rect coreBounds;
    private final ColorMatrix saturation=new ColorMatrix();
    private boolean enabled,bypass,pending,target;
    private float power,fromPower,progress=1;
    private ValueAnimator animator;
    private Runnable motionFinished;
    GlassGooseView(Context context,boolean enabled,boolean bypass,boolean light){
        super(context);this.enabled=enabled;this.bypass=bypass;this.light=light;power=enabled?1:0;
        BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=2;
        core=BitmapFactory.decodeResource(getResources(),light?R.drawable.goose_core_light:R.drawable.goose_core_dark,decode);
        // The production assets include transparent breathing room; fit the lens.
        coreBounds=core==null?null:new Rect((int)(core.getWidth()*.045f),(int)(core.getHeight()*.045f),(int)(core.getWidth()*.955f),(int)(core.getHeight()*.955f));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setMotionFinished(Runnable action){motionFinished=action;}
    boolean isAnimating(){return animator!=null;}
    boolean isPending(){return pending;}
    void beginSwitch(boolean enable){pending=true;target=enable;animateTo(enable?1:0,1250);}
    void commit(boolean enable,boolean direct){
        boolean wasPending=pending;enabled=enable;bypass=direct;pending=false;
        if((animator!=null&&target!=enable)||(animator==null&&wasPending&&Math.abs(power-(enable?1:0))>.01f))animateTo(enable?1:0,550);
        else if(animator==null){power=enable?1:0;progress=1;invalidate();}
    }
    void sync(boolean enable,boolean direct){if(pending)return;enabled=enable;bypass=direct;if(animator==null){power=enable?1:0;invalidate();}}
    private void animateTo(float value,long duration){
        cancelAnimator();fromPower=power;target=value>0;progress=0;
        if(!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE){power=value;progress=1;invalidate();if(motionFinished!=null)motionFinished.run();return;}
        ValueAnimator next=ValueAnimator.ofFloat(0,1);animator=next;next.setDuration(duration);next.setInterpolator(new PathInterpolator(.2f,0,.15f,1));
        next.addUpdateListener(a->{progress=(float)a.getAnimatedValue();power=fromPower+(value-fromPower)*progress;invalidate();});
        next.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(animator!=a)return;animator=null;progress=1;power=pending?value:(enabled?1:0);invalidate();if(motionFinished!=null)motionFinished.run();}});next.start();
    }
    private void cancelAnimator(){if(animator!=null){ValueAnimator previous=animator;animator=null;previous.removeAllUpdateListeners();previous.removeAllListeners();previous.cancel();}}
    void stopMotion(){cancelAnimator();progress=1;power=enabled?1:0;invalidate();}
    @Override protected void onDetachedFromWindow(){stopMotion();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)stopMotion();}
    private int blend(int a,int b,float t){t=Math.max(0,Math.min(1,t));return Color.argb((int)(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t),(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private void fill(int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setAlpha(Color.alpha(color));}
    private void gradient(Shader shader){paint.setShader(shader);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);}
    private Path circle(float radius){Path p=new Path();p.addCircle(0,0,radius,Path.Direction.CW);return p;}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float x=getWidth()*.5f,y=getHeight()*.48f,r=Math.min(getWidth()*.225f,getHeight()*.355f);
        float pulse=animator==null?0:(float)Math.sin(Math.PI*progress),lift=pulse*r*.035f,scale=.95f+.05f*power+.035f*pulse;
        canvas.save();canvas.translate(x,y-lift);canvas.scale(scale,scale);x=0;y=0;
        int blue=blend(light?0xffaab3c5:0xff455065,0xff648ef7,power),violet=blend(light?0xffc7c4cf:0xff646078,0xffae91ee,power),cyan=blend(light?0xffe4e8ed:0xff7a8b9e,bypass?0xffa9e5e1:0xffa6e9ff,power);
        gradient(new RadialGradient(x,y,r*1.9f,new int[]{blend(0x0a778399,light?0x297e88d4:0x60455dca,power),0x087a8ac2,0x00000000},new float[]{0,.65f,1},Shader.TileMode.CLAMP));canvas.drawCircle(x,y,r*1.9f,paint);
        // Rear orbit precedes the sphere; the front half crosses the lens.
        canvas.save();canvas.rotate(-21+pulse*(target?9:-9));RectF orbit=new RectF(-r*1.48f,-r*.37f,r*1.48f,r*.37f);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,r*.009f));paint.setAlpha(255);paint.setShader(new LinearGradient(orbit.left,0,orbit.right,0,new int[]{0x087b8cc5,blend(0x406f7d9a,light?0x708b83c6:0x907c9fef,power),0x087b8cc5},null,Shader.TileMode.CLAMP));canvas.drawArc(orbit,180,180,false,paint);paint.setShader(null);canvas.restore();
        if(core!=null){
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setAlpha((int)(190+65*power));paint.setFilterBitmap(true);
            saturation.setSaturation(.12f+.88f*power);paint.setColorFilter(new ColorMatrixColorFilter(saturation));
            canvas.drawBitmap(core,coreBounds,new RectF(-r,-r,r,r),paint);paint.setColorFilter(null);paint.setAlpha(255);
        }else{
        gradient(new RadialGradient(-r*.35f,-r*.46f,r*1.65f,new int[]{blend(light?0xffedf0f5:0xff697a96,light?0xffe4f6ff:0xff9bdcf5,power),blue,blend(light?0xffd4d3df:0xff3d405e,light?0xffb3abe5:0xff484a9d,power),violet},new float[]{0,.36f,.75f,1},Shader.TileMode.CLAMP));canvas.drawCircle(0,0,r,paint);
        canvas.save();canvas.clipPath(circle(r));
        for(int i=0;i<7;i++){
            float offset=i*r*.12f;Path wave=new Path();wave.moveTo(-r*1.12f,-r*.58f+offset);wave.cubicTo(-r*.2f,-r*1.05f+offset,-r*.06f,r*.76f+offset,r*1.05f,r*.12f+offset);
            paint.setShader(new LinearGradient(-r,-r,r,r,new int[]{0x70e2fbff,0x316c8dc8,0x63dac7ff},null,Shader.TileMode.CLAMP));paint.setAlpha((int)(110+100*power));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*(i%3==0?.062f:.025f));canvas.drawPath(wave,paint);
        }
        gradient(new RadialGradient(-r*.4f,-r*.56f,r*1.1f,new int[]{0x9cffffff,0x20dff7ff,0x00000000},null,Shader.TileMode.CLAMP));canvas.drawCircle(-r*.3f,-r*.3f,r*1.3f,paint);
        canvas.save();canvas.translate(0,r*.06f-lift*.45f);canvas.rotate(pulse*(target?-2.5f:2));canvas.scale(r*.77f,r*.77f);drawGoose(canvas,pulse);canvas.restore();
        gradient(new RadialGradient(r*.52f,r*.6f,r*1.24f,new int[]{blend(0x287f8da6,0x486b68b2,power),0x007b88b6},null,Shader.TileMode.CLAMP));canvas.drawCircle(0,0,r*1.2f,paint);canvas.restore();
        paint.setStyle(Paint.Style.STROKE);paint.setAlpha(255);paint.setStrokeWidth(r*.018f);paint.setShader(new LinearGradient(-r,-r,r,r,new int[]{0xb9e3fbff,0x407a8fc7,0xa3d0b9ff},null,Shader.TileMode.CLAMP));canvas.drawCircle(0,0,r,paint);paint.setShader(null);
        paint.setColor(light?0x77ffffff:0x9ccbf4ff);paint.setStrokeWidth(r*.028f);canvas.drawArc(new RectF(-r*.965f,-r*.965f,r*.965f,r*.965f),197,70,false,paint);
        }
        canvas.save();canvas.rotate(-21+pulse*(target?9:-9));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,r*.01f));paint.setAlpha(255);paint.setShader(new LinearGradient(orbit.left,0,orbit.right,0,new int[]{0x007a8fd2,blend(0x658a93ac,light?0x88958bcc:0xc9b8d4ff,power),0x007a8fd2},null,Shader.TileMode.CLAMP));canvas.drawArc(orbit,0,180,false,paint);paint.setShader(null);
        double angle=Math.toRadians(-32+(animator==null?0:(target?1:-1)*progress*360));float px=(float)Math.cos(angle)*r*1.48f,py=(float)Math.sin(angle)*r*.37f;
        gradient(new RadialGradient(px-r*.012f,py-r*.015f,r*.065f,new int[]{0xfff3ffff,cyan,violet},null,Shader.TileMode.CLAMP));canvas.drawCircle(px,py,r*.055f,paint);canvas.restore();
        if(animator!=null){
            for(int i=0;i<10;i++){double angle2=i*2.39996323+progress*(target?1.6:-1.6);float distance=r*(target?1.7f-.63f*progress:1.04f+.7f*progress),alpha=pulse*(i%3==0?.85f:.5f);fill(Color.argb((int)(alpha*220),175,190,255));canvas.drawCircle((float)Math.cos(angle2)*distance,(float)Math.sin(angle2)*distance*.65f,r*(i%3==0?.027f:.015f),paint);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*.014f);paint.setColor(Color.argb((int)(pulse*(target?105:70)),172,196,255));canvas.drawCircle(0,0,r*(target?1.48f-.35f*progress:1.07f+.42f*progress),paint);
        }
        canvas.restore();paint.setShader(null);paint.setAlpha(255);
    }
    private void drawGoose(Canvas c,float motion){
        int orange=blend(0xffc3b59f,0xfff3b05e,power);
        Path foot=new Path();foot.moveTo(-.59f,.22f);foot.lineTo(-.7f,.48f);foot.quadTo(-.7f,.56f,-.64f,.61f);foot.lineTo(-.57f,.67f);foot.quadTo(-.52f,.68f,-.52f,.58f);foot.lineTo(-.44f,.62f);foot.quadTo(-.36f,.62f,-.43f,.54f);foot.lineTo(-.55f,.46f);foot.lineTo(-.48f,.26f);foot.close();fill(orange);c.drawPath(foot,paint);
        foot.reset();foot.moveTo(.0f,.23f);foot.lineTo(.22f,.48f);foot.lineTo(.5f,.56f);foot.quadTo(.58f,.6f,.5f,.65f);foot.lineTo(.38f,.65f);foot.quadTo(.32f,.73f,.28f,.65f);foot.lineTo(.11f,.57f);foot.lineTo(-.13f,.29f);foot.close();c.drawPath(foot,paint);
        Path knife=new Path();knife.moveTo(.83f,-.12f);knife.lineTo(1.13f,-.43f);knife.quadTo(1.22f,-.49f,1.23f,-.4f);knife.quadTo(1.18f,-.27f,.98f,-.13f);knife.close();gradient(new LinearGradient(.8f,-.4f,1.2f,-.1f,new int[]{0xffd6d8df,0xffa8aab8},null,Shader.TileMode.CLAMP));c.drawPath(knife,paint);
        Path body=new Path();body.moveTo(-.77f,.18f);body.cubicTo(-.86f,-.02f,-.86f,-.44f,-.95f,-.68f);body.cubicTo(-1.02f,-.89f,-.79f,-.73f,-.61f,-.62f);body.cubicTo(-.37f,-.51f,-.29f,-.27f,-.2f,-.18f);body.cubicTo(.02f,-.32f,.26f,-.34f,.42f,-.32f);body.cubicTo(.55f,-.48f,.8f,-.41f,.85f,-.23f);body.lineTo(.82f,-.07f);body.cubicTo(.68f,.02f,.41f,-.04f,.16f,.03f);body.cubicTo(.03f,.31f,-.21f,.42f,-.56f,.33f);body.quadTo(-.7f,.3f,-.77f,.18f);body.close();gradient(new LinearGradient(-.5f,-.65f,.05f,.4f,new int[]{0xffffffff,0xfffbfcff,blend(0xffe1e4ed,0xffd8dff8,power)},new float[]{0,.5f,1},Shader.TileMode.CLAMP));c.drawPath(body,paint);
        c.save();c.rotate(-motion*3,-.53f,-.18f);Path wing=new Path();wing.moveTo(-.92f,-.68f);wing.cubicTo(-.65f,-.39f,-.34f,-.31f,-.19f,-.15f);wing.cubicTo(-.45f,-.15f,-.7f,-.23f,-.82f,-.4f);wing.quadTo(-.94f,-.56f,-.92f,-.68f);wing.close();gradient(new LinearGradient(-.8f,-.6f,-.45f,-.18f,new int[]{0xfffeffff,0xffe1e6f6},null,Shader.TileMode.CLAMP));c.drawPath(wing,paint);c.restore();
        Path bill=new Path();bill.moveTo(.79f,-.26f);bill.lineTo(1.01f,-.13f);bill.quadTo(1.07f,-.07f,.95f,-.02f);bill.lineTo(.75f,-.06f);bill.close();gradient(new LinearGradient(.8f,-.25f,.99f,0,new int[]{orange,blend(0xffd9cab1,0xffffd78b,power)},null,Shader.TileMode.CLAMP));c.drawPath(bill,paint);
        fill(0xff34374b);c.drawCircle(.67f,-.27f,.023f,paint);fill(0xaaffffff);c.drawCircle(.664f,-.278f,.007f,paint);
    }
}
