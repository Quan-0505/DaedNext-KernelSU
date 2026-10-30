package io.github.quan0505.daednext;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Translates explicit UI literals only; never translates user or API data. */
final class L10n {
    private static volatile boolean english;
    private static volatile Map<String,String> translations;
    private static final String[] MODES={"system","zh","en"};
    static String mode(Context c){return c.getSharedPreferences("language",Context.MODE_PRIVATE).getString("mode","system");}
    static int selected(Context c){String mode=mode(c);for(int i=0;i<MODES.length;i++)if(MODES[i].equals(mode))return i;return 0;}
    static void save(Context c,int index){if(index<0||index>=MODES.length)throw new IllegalArgumentException("Invalid language");c.getSharedPreferences("language",Context.MODE_PRIVATE).edit().putString("mode",MODES[index]).apply();}
    static String[] options(){return new String[]{english?"Follow system":"跟随系统","简体中文","English"};}
    static Context attach(Context base){
        String mode=mode(base);Locale system=Resources.getSystem().getConfiguration().getLocales().get(0);
        english="en".equals(mode)||(!"zh".equals(mode)&&!"zh".equals(system.getLanguage()));
        if(translations==null){
            Map<String,String> map=new HashMap<>();
            try(InputStream in=base.getResources().openRawResource(R.raw.strings_en);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                JSONObject json=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();map.put(key,json.getString(key));}
            }catch(Exception ignored){}
            translations=Collections.unmodifiableMap(map);
        }
        Configuration config=new Configuration(base.getResources().getConfiguration());config.setLocale(english?Locale.ENGLISH:Locale.SIMPLIFIED_CHINESE);
        return base.createConfigurationContext(config);
    }
    static boolean english(){return english;}
    static String tr(String literal){Map<String,String> map=translations;if(literal==null||!english||map==null)return literal;String value=map.get(literal);return value==null?literal:value;}
    static String[] labels(String[] source){String[] result=source.clone();for(int i=0;i<result.length;i++)result[i]=tr(result[i]);return result;}
}
