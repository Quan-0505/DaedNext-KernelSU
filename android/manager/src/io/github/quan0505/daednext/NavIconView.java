package io.github.quan0505.daednext;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Consistent outline icons, independent of platform emoji fonts. */
final class NavIconView extends View {
    private final int kind,color;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    NavIconView(Context context,int kind,int color){super(context);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        canvas.save();canvas.scale(getWidth()/28f,getHeight()/28f);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path p=new Path();
        switch(kind){
            case 0:
                p.moveTo(5,12);p.lineTo(14,4);p.lineTo(23,12);p.lineTo(23,23);p.lineTo(17,23);p.lineTo(17,16);p.lineTo(11,16);p.lineTo(11,23);p.lineTo(5,23);p.close();canvas.drawPath(p,paint);break;
            case 1:
                canvas.drawLine(8,8,21,14,paint);canvas.drawLine(8,20,21,14,paint);canvas.drawLine(8,8,8,20,paint);
                paint.setStyle(Paint.Style.FILL);canvas.drawCircle(8,8,3,paint);canvas.drawCircle(8,20,3,paint);canvas.drawCircle(21,14,3,paint);break;
            case 2:
                canvas.drawRoundRect(new RectF(5,7,23,24),3,3,paint);canvas.drawLine(9,3,19,3,paint);canvas.drawLine(10,12,18,12,paint);canvas.drawLine(10,17,16,17,paint);break;
            case 3:
                canvas.drawArc(new RectF(2,5,26,27),223,94,false,paint);canvas.drawArc(new RectF(7,11,21,25),222,96,false,paint);
                paint.setStyle(Paint.Style.FILL);canvas.drawCircle(14,23,1.8f,paint);break;
            default:
                canvas.drawCircle(14,14,8,paint);canvas.drawCircle(14,14,3,paint);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(14+(float)Math.cos(a)*8,14+(float)Math.sin(a)*8,14+(float)Math.cos(a)*11,14+(float)Math.sin(a)*11,paint);}break;
        }
        canvas.restore();
    }
}
