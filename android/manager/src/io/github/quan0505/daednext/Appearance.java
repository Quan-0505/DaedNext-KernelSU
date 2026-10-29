package io.github.quan0505.daednext;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

/** App-local appearance; independent of module and proxy configuration. */
final class Appearance {
    static final String[] MODES = {"system", "light", "dark"};
    static final String[] LABELS = {"跟随系统", "浅色 · 云雾", "深色 · 星夜"};
    static String mode(Context c) { return c.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("mode", "system"); }
    static int selected(Context c) { String mode=mode(c); for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))return i; return 0; }
    static void save(Context c, int index) { c.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", MODES[index]).apply(); }
    final boolean light;
    final int ink, muted, background, card, border, accent, control, primary, primaryInk, nav, selected, danger;
    Appearance(Context c) {
        String mode=mode(c);
        light="light".equals(mode)||(!"dark".equals(mode)&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES);
        ink=light?0xff282a3d:0xfff6f5fa; muted=light?0xff686b7d:0xff9696a6;
        background=light?0xfff5f4f0:0xff090a10; card=light?0xfffffefd:0xff171820;
        border=light?0xffe4e2eb:0xff292a36; accent=light?0xff6352a3:0xffb4aeff;
        control=light?0xffeeedf5:0xff242630; primary=light?0xff403750:0xfff4f3fa;
        primaryInk=light?0xfffaf8ff:0xff101119; nav=light?0xfffdfcfa:0xff1b1c25;
        selected=light?0xffe6e1f3:0xff303044; danger=light?0xffac3b53:0xffff9ba9;
    }
    void apply(Activity a) { a.setTheme(light?R.style.AppThemeLight:R.style.AppTheme); }
    void bars(Activity a) {
        a.getWindow().setStatusBarColor(background);a.getWindow().setNavigationBarColor(background);
        a.getWindow().getDecorView().setSystemUiVisibility(light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
    }
}
