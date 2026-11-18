package org.intraportmap.android;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

final class AppStyle {
    static final int BRAND=Color.rgb(23,107,206), BACKGROUND=Color.rgb(243,246,250);
    static int dp(View view,int value) { return (int)(view.getResources().getDisplayMetrics().density*value); }
    static GradientDrawable surface(View view,int color,int border,int radius) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(color);
        drawable.setCornerRadius(dp(view,radius));
        if(border!=0) drawable.setStroke(dp(view,1),border);
        return drawable;
    }
    static void tab(Button button,boolean selected) {
        button.setTextColor(selected ? BRAND : Color.rgb(94,111,130));
        button.setBackground(surface(button,selected ? Color.rgb(225,237,253) : Color.WHITE,0,12));
        button.setElevation(0);
    }
    static void apply(View view) {
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) apply(group.getChildAt(i));
        }
        if(view instanceof EditText || view instanceof Spinner) {
            view.setBackground(surface(view,Color.WHITE,Color.rgb(217,225,235),12));
            view.setPadding(dp(view,14),dp(view,12),dp(view,14),dp(view,12));
            view.setMinimumHeight(dp(view,52));
            ViewGroup.LayoutParams params=view.getLayoutParams();
            if(params instanceof ViewGroup.MarginLayoutParams) ((ViewGroup.MarginLayoutParams)params).bottomMargin=dp(view,4);
        }
        if(view instanceof EditText) {
            EditText text=(EditText)view; text.setTextColor(Color.rgb(24,45,65)); text.setHintTextColor(Color.rgb(137,150,166));
        } else if(view instanceof Button && !(view instanceof Switch)) {
            Button button=(Button)view; String title=button.getText().toString();
            boolean primary=title.contains("启动"), stop=title.startsWith("停止");
            button.setAllCaps(false); button.setTextSize(15); button.setTypeface(null,Typeface.BOLD);
            button.setTextColor(primary ? Color.WHITE : stop ? Color.rgb(183,59,59) : BRAND);
            button.setBackgroundTintList(null);
            button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(35,23,107,206)),
                surface(view,primary ? BRAND : Color.WHITE,primary ? 0 : Color.rgb(217,225,235),14),null));
            button.setMinimumHeight(dp(view,50)); button.setPadding(dp(view,14),dp(view,10),dp(view,14),dp(view,10));
            button.setElevation(primary ? dp(view,2) : 0);
            ViewGroup.LayoutParams params=view.getLayoutParams();
            if(params instanceof ViewGroup.MarginLayoutParams) ((ViewGroup.MarginLayoutParams)params).topMargin=dp(view,10);
        } else if(view instanceof TextView && !(view instanceof Switch)) {
            TextView text=(TextView)view;
            float size=text.getTextSize()/view.getResources().getDisplayMetrics().scaledDensity;
            text.setTextColor(size>=16 ? Color.rgb(24,45,65) : Color.rgb(94,111,130));
            text.setLineSpacing(dp(view,3),1);
            if(size>=18) text.setTypeface(null,Typeface.BOLD);
        }
    }
    static void status(TextView text) {
        text.setTextSize(14); text.setTextColor(BRAND);
        text.setBackground(surface(text,Color.rgb(225,237,253),0,12));
        text.setPadding(dp(text,14),dp(text,12),dp(text,14),dp(text,12));
        ViewGroup.LayoutParams params=text.getLayoutParams();
        if(params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams)params).topMargin=dp(text,18);
            ((ViewGroup.MarginLayoutParams)params).bottomMargin=dp(text,8);
        }
    }
}
