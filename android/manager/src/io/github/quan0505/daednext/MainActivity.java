package io.github.quan0505.daednext;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Native manager: module state is authoritative; no direct database writes. */
public final class MainActivity extends Activity {
    private final ModuleClient module = new ModuleClient();
    private final ApiClient api = new ApiClient(module);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout content, nav;
    private ScrollView scroll;
    private TextView pageTitle, notice;
    private AlertDialog formDialog;
    private JSONObject status, wifiConfig, metrics;
    private String mode = "blacklist";
    private EditText wifiNames;
    private Switch wifiEnabled, cellular;
    private final List<View> actions = new ArrayList<>();
    private boolean busy, foreground, reading, wifiDraft;
    private int page, generation;
    private Appearance appearance;
    private int ink, muted, background, card, border, accent;
    private final String[] pages = {"仪表盘", "节点", "订阅", "网络", "设置"};
    private final Runnable ticker = new Runnable() { public void run() {
        if (foreground) { refreshStatus(false); main.postDelayed(this, 6000); }
    }};

    @Override public void onCreate(Bundle saved) {
        appearance=new Appearance(this); appearance.apply(this);
        super.onCreate(saved);
        ink=appearance.ink; muted=appearance.muted; background=appearance.background;
        card=appearance.card; border=appearance.border; accent=appearance.accent;
        appearance.bars(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(background);
        root.setFitsSystemWindows(true); setContentView(root);
        LinearLayout header = column(); header.setPadding(dp(24), dp(12), dp(24), dp(3));
        LinearLayout brandRow=row();
        TextView brand = text("DaedNext", 22, ink); brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        brandRow.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("KernelSU",10,muted);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(shape(card,20));brandRow.addView(badge);header.addView(brandRow);
        pageTitle = text("你的连接", 31, ink); pageTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,-2);titleParams.topMargin=dp(15);header.addView(pageTitle,titleParams);
        root.addView(header);
        notice = text("正在连接本机服务…", 11, muted); notice.setPadding(dp(24), dp(2), dp(24), dp(6)); root.addView(notice);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = column(); content.setPadding(dp(20), dp(4), dp(20), dp(16)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = row(); nav.setPadding(dp(5), dp(7), dp(5), dp(7)); nav.setBackground(shape(appearance.nav,28));
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,-2);navParams.setMargins(dp(18),dp(5),dp(18),dp(12));
        root.addView(nav,navParams); buildNav(); showPage(saved==null?0:saved.getInt("page",0));
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("page",page); super.onSaveInstanceState(out); }
    @Override protected void onResume() { super.onResume(); foreground = true; main.removeCallbacks(ticker); main.post(ticker); }
    @Override protected void onPause() { foreground = false; main.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow(); api.clear(); super.onDestroy(); }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(1); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(0); v.setBaselineAligned(false);v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1);t.setPadding(0, dp(3), 0, dp(3)); return t; }
    private GradientDrawable shape(int fill, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), border); return d; }
    private LinearLayout card(String title) {
        LinearLayout v = column(); v.setPadding(dp(18), dp(16), dp(18), dp(18)); v.setBackground(shape(card, 24));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); content.addView(v, p);
        if (!title.isEmpty()) { TextView t = text(title, 17, ink); t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); v.addView(t); }
        return v;
    }
    private void hint(LinearLayout parent, String value) { parent.addView(text(value, 13, muted)); }
    private Button button(LinearLayout parent, String title, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setTextSize(14); b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setStateListAnimator(null);
        b.setTextColor(primary ? appearance.primaryInk : ink);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(appearance.light?0x143f315d:0x18ffffff),shape(primary ? appearance.primary : appearance.control, 28),null));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(49)); p.topMargin = dp(10); parent.addView(b, p);
        b.setEnabled(!busy); b.setOnClickListener(v -> { if (!busy) action.run(); }); actions.add(b); return b;
    }
    private Button[] paired(LinearLayout parent,String left,Runnable first,String right,Runnable second) {
        LinearLayout line=row();line.setGravity(Gravity.TOP);parent.addView(line);
        Button a=button(line,left,false,first),b=button(line,right,false,second);
        LinearLayout.LayoutParams pa=new LinearLayout.LayoutParams(0,dp(48),1);pa.topMargin=dp(8);pa.rightMargin=dp(5);a.setLayoutParams(pa);
        LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(0,dp(48),1);pb.topMargin=dp(8);pb.leftMargin=dp(5);b.setLayoutParams(pb);
        return new Button[]{a,b};
    }
    private void buildNav() {
        nav.removeAllViews();
        for (int i=0;i<pages.length;i++) {
            final int index=i; LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(3),0,dp(3));
            android.widget.FrameLayout iconFrame=new android.widget.FrameLayout(this);
            if(i==page)iconFrame.setBackground(shape(appearance.selected,13));
            NavIconView icon=new NavIconView(this,i,i==page?accent:muted);
            android.widget.FrameLayout.LayoutParams iconParams=new android.widget.FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER);iconFrame.addView(icon,iconParams);
            item.addView(iconFrame,new LinearLayout.LayoutParams(dp(42),dp(31)));
            TextView label=text(pages[i].equals("仪表盘")?"首页":pages[i],10,i==page?ink:muted);
            label.setGravity(Gravity.CENTER);
            item.addView(label,new LinearLayout.LayoutParams(-1,-2));
            item.setContentDescription(pages[i]); item.setOnClickListener(v -> {
                if (busy || index==page) return;
                if (page==3 && wifiDraft) new AlertDialog.Builder(this).setTitle("尚未保存 Wi-Fi 设置")
                    .setMessage("离开将放弃当前修改。")
                    .setPositiveButton("离开",(d,w)->showPage(index)).setNegativeButton("继续编辑",null).show();
                else showPage(index);
            }); nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    private void showPage(int target) {
        page=target; generation++; actions.clear(); content.removeAllViews(); pageTitle.setText(page==0?"你的连接":pages[page]); buildNav(); wifiDraft=false;
        if (page==0) dashboard();
        else if (page==1) loadNodes();
        else if (page==2) loadSubscriptions();
        else if (page==3) loadWifi();
        else settings();
        scroll.post(()->scroll.scrollTo(0,0));
        content.setAlpha(0);content.animate().alpha(1).setDuration(180).start();
    }
    private void error(Exception error) {
        String message=error.getMessage(); if (message==null || message.isEmpty()) message="操作失败，请检查服务状态";
        notice.setText(message); notice.setTextColor(appearance.danger);
        new AlertDialog.Builder(this).setTitle("需要处理").setMessage(message).setPositiveButton("知道了",null).show();
    }
    interface Job<T> { T run() throws Exception; }
    interface Done<T> { void accept(T value) throws Exception; }
    private <T> void task(String label, Job<T> job, Done<T> done) {
        if (busy) return; busy=true; notice.setText(label); notice.setTextColor(accent);
        for(View v:actions)v.setEnabled(false);
        int epoch=generation;
        worker.execute(()->{
            T result=null; Exception failure=null; try { result=job.run(); } catch(Exception e) { failure=e; }
            final T value=result; final Exception problem=failure;
            main.post(()->{ if(isFinishing() || isDestroyed())return;
                busy=false; for(View v:actions)v.setEnabled(true);
                if(problem!=null) { error(problem); return; }
                if(formDialog!=null){formDialog.dismiss();formDialog=null;}
                notice.setText("已与本机服务同步"); notice.setTextColor(muted);
                if(epoch==generation)try{done.accept(value);}catch(Exception e){error(e);}
            });
        });
    }
    private void refreshStatus(boolean force) {
        if(busy || reading)return; reading=true; final int epoch=generation,selectedPage=page;
        worker.execute(()->{
            JSONObject value=null,sample=null;Exception failure=null;
            try { value=module.status(); } catch(Exception e){failure=e;}
            if(value!=null && !value.isNull("pid") && selectedPage==0)try{sample=api.get("runtime/overview");}catch(Exception ignored){}
            JSONObject result=value,statistics=sample; Exception problem=failure;
            main.post(()->{reading=false;if(isFinishing()||isDestroyed())return;
                if(problem!=null){ notice.setText(problem.getMessage()); notice.setTextColor(appearance.danger); if(force)error(problem); return; }
                boolean firstStatus=status==null;
                status=result;if(selectedPage==0)metrics=statistics;
                notice.setText(serviceOn()?"本机服务已连接":"后台服务未启动");notice.setTextColor(muted);
                if(page==0 && epoch==generation && !busy){content.removeAllViews();actions.clear();dashboard();}
                else if(page==4 && firstStatus && epoch==generation && !busy){content.removeAllViews();actions.clear();settings();}
            });
        });
    }
    private JSONObject ready() { return status==null?new JSONObject():status.optJSONObject("readiness")==null?new JSONObject():status.optJSONObject("readiness"); }
    private boolean serviceOn() { return status!=null && !status.isNull("pid"); }
    private void dashboard() {
        JSONObject r=ready(); boolean wanted=r.optBoolean("runtimeRequired"), active=r.optBoolean("runtimeRunning"), bypass=r.optBoolean("networkPolicyPaused");
        LinearLayout hero=column();hero.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams heroParams=new LinearLayout.LayoutParams(-1,-2);heroParams.bottomMargin=dp(18);content.addView(hero,heroParams);
        hero.addView(new ConnectionArtView(this,wanted,bypass,appearance.light),new LinearLayout.LayoutParams(-1,dp(175)));
        String state=status==null?"等待连接":!serviceOn()?"服务未启动":bypass&&wanted?"已自动直连":active?"代理运行中":wanted?"正在恢复连接":"代理已暂停";
        TextView title=text(state,29,ink);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setGravity(Gravity.CENTER);hero.addView(title);
        TextView detail=text(bypass&&wanted?"当前 Wi-Fi 使用上游网络\n离开后，手机代理会自动接管。":active?"连接已由 DaedNext 接管\n按你的规则，自由切换出口。":"保留你的节点与规则\n启用后，按网络策略自动连接。",13,muted);
        detail.setGravity(Gravity.CENTER);LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);detailParams.topMargin=dp(6);detailParams.bottomMargin=dp(10);hero.addView(detail,detailParams);
        button(hero,wanted?"暂停代理":"启用代理",true,()->task(wanted?"正在暂停代理…":"正在启用代理…",()->{
            if(!serviceOn())module.command("start");
            api.post(wanted?"runtime/stop":"runtime/reload",new JSONObject());return module.status();
        },s->{status=s;showPage(0);}));
        LinearLayout network=card("");JSONObject w=status==null?null:status.optJSONObject("wifiPolicy");
        LinearLayout networkRow=row();network.addView(networkRow);
        android.widget.FrameLayout wifiIcon=new android.widget.FrameLayout(this);wifiIcon.setBackground(shape(appearance.control,15));
        wifiIcon.addView(new NavIconView(this,3,accent),new android.widget.FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER));
        networkRow.addView(wifiIcon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout networkText=column();LinearLayout.LayoutParams networkParams=new LinearLayout.LayoutParams(0,-2,1);networkParams.leftMargin=dp(13);networkRow.addView(networkText,networkParams);
        String iface=status==null?"":status.optString("interface");
        String name=w!=null&&!w.isNull("ssid")?w.optString("ssid"):iface.startsWith("wlan")?"Wi-Fi":iface.startsWith("rmnet")?"移动数据":"当前网络";
        networkText.addView(text(name,18,ink));
        hint(networkText,w==null?"等待网络策略":w.optBoolean("enabled")?(w.optString("mode").equals("blacklist")?"黑名单":"白名单")+" · "+(w.optBoolean("direct")?"手机直连":"使用代理"):"按现有规则连接");
        TextView chevron=text("›",25,muted);networkRow.addView(chevron);network.setContentDescription("网络策略");
        network.setOnClickListener(v->{if(!busy)showPage(3);});actions.add(network);

        workspaceEntries();

        LinearLayout rates=row();LinearLayout.LayoutParams ratesParams=new LinearLayout.LayoutParams(-1,-2);ratesParams.bottomMargin=dp(12);content.addView(rates,ratesParams);
        metric(rates,"下载速度",metrics==null?"—":bytes(metrics.optDouble("downloadRate"))+"/s",appearance.light?0xff405d97:0xffb8c8ff,true);
        metric(rates,"上传速度",metrics==null?"—":bytes(metrics.optDouble("uploadRate"))+"/s",appearance.light?0xff795399:0xffd0b7fb,false);
        TextView scope=text(bypass&&wanted?"直连期间，Wi-Fi 上游流量不计入这里。":"仅统计由手机代理转发的流量。",10,muted);scope.setPadding(dp(3),0,dp(3),dp(12));content.addView(scope);

        LinearLayout advanced=card("");LinearLayout advancedRow=row();advanced.addView(advancedRow);
        LinearLayout advancedText=column();advancedRow.addView(advancedText,new LinearLayout.LayoutParams(0,-2,1));
        advancedText.addView(text("高级管理面板",16,ink));hint(advancedText,"策略组、DNS 与路由规则");advancedRow.addView(text("↗",22,muted));
        advanced.setOnClickListener(v->{if(!busy)panel("/");});actions.add(advanced);
        button(content,"查看运行详情",false,()->task("正在读取运行概况…",()->api.get("runtime/overview"),this::showMetrics));
        if(status==null)button(content,"连接模块 / 申请 Root 授权",false,()->refreshStatus(true));
    }
    private void metric(LinearLayout parent,String label,String value,int color,boolean first){
        LinearLayout box=column();box.setPadding(dp(17),dp(16),dp(12),dp(16));box.setBackground(shape(card,22));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(first)params.rightMargin=dp(5);else params.leftMargin=dp(5);parent.addView(box,params);
        box.addView(text(label,11,muted));TextView number=text(value,22,color);number.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));box.addView(number);
    }
    private void showMetrics(JSONObject j){
        new AlertDialog.Builder(this).setTitle("运行概况").setMessage("上传速度："+bytes(j.optDouble("uploadRate"))+"/s\n下载速度："+bytes(j.optDouble("downloadRate"))+"/s\n累计上传："+bytes(j.optDouble("uploadTotal"))+"\n累计下载："+bytes(j.optDouble("downloadTotal"))+"\n内存："+bytes(j.optDouble("rssBytes"))+"\nCPU："+String.format(Locale.ROOT,"%.1f%%",j.optDouble("cpuUsagePercent",0))).setPositiveButton("完成",null).show();
    }
    private static String bytes(double n) { if(Double.isNaN(n)||n<0)return "—"; if(n<1024)return String.format(Locale.ROOT,"%.0f B",n);if(n<1048576)return String.format(Locale.ROOT,"%.1f KB",n/1024);if(n<1073741824)return String.format(Locale.ROOT,"%.1f MB",n/1048576);return String.format(Locale.ROOT,"%.2f GB",n/1073741824); }
    private void panel(String path) { Intent intent=new Intent(this,PanelActivity.class);intent.putExtra("path",path);startActivity(intent); }
    private void workspaceEntries() {
        LinearLayout entries=card("配置与群组");
        hint(entries,"管理核心参数、代理出口与自动优选策略。");
        paired(entries,"配置",()->panel("/?panel=config"),"群组",()->panel("/?panel=group"));
    }
    private JSONArray items(JSONObject j) { JSONArray a=j.optJSONArray("items");return a==null?new JSONArray():a; }
    private void loadNodes() {
        LinearLayout tools=card("节点管理");hint(tools,"导入节点、查看延迟，使用策略组管理代理出口。");
        paired(tools,"导入节点链接",this::importNodes,"策略组",()->panel("/?panel=group"));
        button(tools,"刷新节点 / 延迟",false,()->showPage(1));
        task("正在读取节点…",()->{
            JSONArray all=items(api.get("nodes")), subscribed=items(api.get("nodes?independent=false"));
            for(int i=0;i<subscribed.length();i++)all.put(subscribed.get(i));
            JSONObject result=new JSONObject();result.put("nodes",all);result.put("latencies",api.get("nodes/latencies"));return result;
        },result->{
            JSONArray all=result.getJSONArray("nodes"), latencies=items(result.getJSONObject("latencies"));
            Map<Long,JSONObject> latencyMap=new HashMap<>();for(int i=0;i<latencies.length();i++){JSONObject l=latencies.getJSONObject(i);latencyMap.put(l.optLong("id"),l);}
            if(all.length()==0)hint(card("还没有节点"),"导入节点，或前往“订阅”添加订阅地址。");
            for(int i=0;i<all.length();i++) {
                JSONObject node=all.getJSONObject(i); LinearLayout c=card(node.optString("name","未命名节点"));
                hint(c,node.optString("protocol","—").toUpperCase(Locale.ROOT)+" · "+(node.isNull("subscriptionId")?"独立节点":"订阅节点"));
                JSONObject latency=latencyMap.get(node.optLong("id"));
                hint(c,latency==null?"尚无延迟记录":latency.optBoolean("alive")?"延迟 "+latency.optString("latencyMs","—")+" ms":"最近探测未通过");
                paired(c,"测速",()->task("已提交节点测速…",()->api.post("nodes/latencies",new JSONObject().put("nodeIds",new JSONArray().put(node.getLong("id")))),j->Toast.makeText(this,"测速在模块中执行，稍后点击刷新查看结果",Toast.LENGTH_LONG).show()),
                    "管理节点",()->new AlertDialog.Builder(this).setTitle(node.optString("name"))
                    .setItems(new String[]{"编辑名称与链接","删除节点"},(d,index)->{if(index==0)editNode(node);else confirm("删除节点","将从模块中删除该节点。",()->task("正在删除…",()->api.delete("nodes/"+node.optLong("id")),j->showPage(1)));}).show());
            }
            hint(card("共 "+all.length()+" 个节点"),"节点选择、自动优选策略与分流规则可在高级面板中调整。");
        });
    }
    private EditText field(LinearLayout parent,String label,String value,boolean multiline,boolean secret) {
        hint(parent,label);EditText input=new EditText(this);input.setTextColor(ink);input.setTextSize(15);input.setText(value);input.setHintTextColor(muted);
        input.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        input.setBackground(shape(appearance.light?0xfff1eff5:0xff22232d,16));input.setPadding(dp(13),dp(13),dp(13),dp(13));
        input.setMinLines(multiline?3:1);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);params.bottomMargin=dp(12);parent.addView(input,params);return input;
    }
    private void form(String title,LinearLayout fields,Runnable save) {
        ScrollView scroll=new ScrollView(this);scroll.addView(fields);
        formDialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        formDialog.setOnShowListener(d->formDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!busy)save.run();}));
        formDialog.show();
        formDialog.getWindow().setBackgroundDrawable(shape(card,26));
    }
    private LinearLayout formFields() { LinearLayout v=column();v.setPadding(dp(22),dp(8),dp(22),dp(8));return v; }
    private void importNodes() {
        LinearLayout fields=formFields();EditText links=field(fields,"每行一个节点链接","",true,false);
        links.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        form("导入节点",fields,()->task("正在导入节点…",()->{
            JSONArray args=new JSONArray();for(String line:links.getText().toString().split("\\r?\\n"))if(!line.trim().isEmpty()){
                if(!line.trim().matches("[A-Za-z][A-Za-z0-9+.-]*://.+"))throw new Exception("请粘贴完整节点链接，每行一个");
                args.put(new JSONObject().put("link",line.trim()));
            }
            if(args.length()==0)throw new Exception("请填写节点链接");return api.post("nodes",new JSONObject().put("args",args));
        },j->{
            JSONArray results=items(j);int imported=0;
            for(int i=0;i<results.length();i++)if(results.getJSONObject(i).optJSONObject("node")!=null)imported++;
            int failed=results.length()-imported;showPage(1);
            String summary="已导入 "+imported+" 个节点"+(failed>0?"，"+failed+" 个链接未能导入，请检查格式。":"。");
            new AlertDialog.Builder(this).setTitle("导入结果").setMessage(summary).setPositiveButton("完成",null).show();
        }));
    }
    private void editNode(JSONObject node) {
        LinearLayout fields=formFields();EditText name=field(fields,"节点名称",node.optString("name"),false,false);
        EditText link=field(fields,"节点链接（包含凭据）",node.optString("link"),false,true);
        form("编辑节点",fields,()->task("正在保存节点…",()->api.patch("nodes/"+node.optLong("id"),new JSONObject().put("tag",name.getText().toString()).put("link",link.getText().toString())),j->showPage(1)));
    }
    private void loadSubscriptions() {
        LinearLayout tools=card("订阅管理");hint(tools,"添加和更新订阅，集中管理你的代理节点。");
        button(tools,"添加订阅",true,()->subscriptionForm(null));button(tools,"刷新列表",false,()->showPage(2));
        task("正在读取订阅…",()->api.get("subscriptions"),j->{
            JSONArray list=items(j);if(list.length()==0)hint(card("还没有订阅"),"添加自己的 HTTP 或 HTTPS 订阅地址。");
            for(int i=0;i<list.length();i++) {
                JSONObject sub=list.getJSONObject(i);LinearLayout c=card(sub.optString("tag","").isEmpty()?"订阅 #"+sub.optLong("id"):sub.optString("tag"));
                hint(c,sub.optInt("nodeCount")+" 个节点 · "+(sub.optBoolean("cronEnable")?"自动更新已开启":"手动更新"));
                hint(c,"最近更新  "+localTime(sub.optString("updatedAt","")));
                button(c,"更新订阅",true,()->task("正在更新订阅，请稍候…",()->api.post("subscriptions/"+sub.optLong("id")+"/refresh",new JSONObject()),response->{showPage(2);if(response.has("refreshError")&&!response.isNull("refreshError"))textDialog("订阅更新结果",response.opt("refreshError").toString());else Toast.makeText(this,"订阅更新完成",Toast.LENGTH_SHORT).show();}));
                Button[] toolsRow=paired(c,"编辑订阅",()->subscriptionForm(sub),"删除订阅",()->confirm("删除订阅","将删除此订阅及其节点，请确认相关策略组仍有可用节点。",()->task("正在删除订阅…",()->api.delete("subscriptions/"+sub.optLong("id")),response->showPage(2))));
                toolsRow[1].setTextColor(appearance.danger);
            }
        });
    }
    private void subscriptionForm(JSONObject existing) {
        LinearLayout fields=formFields();EditText name=field(fields,"订阅名称",existing==null?"":existing.optString("tag"),false,false);
        EditText link=field(fields,"订阅地址（包含凭据）",existing==null?"":existing.optString("link"),false,true);
        Switch useProxy=new Switch(this);useProxy.setText("通过当前代理更新");useProxy.setTextColor(ink);useProxy.setChecked(existing!=null&&existing.optBoolean("useProxy"));fields.addView(useProxy);
        form(existing==null?"添加订阅":"编辑订阅",fields,()->task("正在保存订阅…",()->{
            String url=link.getText().toString().trim();java.net.URI uri=new java.net.URI(url);
            if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null)throw new Exception("请填写有效的 HTTP 或 HTTPS 订阅地址");
            JSONObject body=new JSONObject().put("tag",name.getText().toString()).put("link",url).put("useProxy",useProxy.isChecked());
            return existing==null?api.post("subscriptions",body):api.patch("subscriptions/"+existing.optLong("id"),body);
        },j->showPage(2)));
    }
    private String localTime(String value){
        try{return java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.getDefault()));}
        catch(Exception error){return "暂未更新";}
    }
    private void loadWifi() {
        hint(card("Wi-Fi 自动切换"),"黑名单中的 Wi-Fi 手机直连，名单外代理；白名单模式相反。移动数据策略独立。");
        task("正在读取网络策略…",()->module.wifi(),info->{
            wifiConfig=new JSONObject(info.getJSONObject("config").toString());mode=wifiConfig.optString("mode","blacklist");
            LinearLayout c=card("自动切换策略");
            wifiEnabled=new Switch(this);wifiEnabled.setText("启用 Wi-Fi 自动切换");wifiEnabled.setTextColor(ink);wifiEnabled.setChecked(wifiConfig.optBoolean("enabled"));c.addView(wifiEnabled);actions.add(wifiEnabled);
            Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"黑名单 · 名单内直连","白名单 · 仅名单内代理"}));spinner.setSelection(mode.equals("blacklist")?0:1);c.addView(spinner);actions.add(spinner);
            wifiNames=field(c,"每行一个 Wi-Fi 名称，精确匹配、区分大小写",join(wifiConfig.optJSONArray(mode)),true,false);wifiNames.setMinLines(5);actions.add(wifiNames);
            cellular=new Switch(this);cellular.setText("非 Wi-Fi 网络使用代理");cellular.setTextColor(ink);cellular.setChecked(wifiConfig.optBoolean("proxy_on_non_wifi",true));c.addView(cellular);actions.add(cellular);
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){String next=pos==0?"blacklist":"whitelist";if(next.equals(mode))return;try{wifiConfig.put(mode,parseNames(wifiNames.getText().toString()));mode=next;wifiNames.setText(join(wifiConfig.optJSONArray(mode)));wifiDraft=true;}catch(Exception e){error(e);}}});
            wifiNames.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){wifiDraft=true;}public void afterTextChanged(android.text.Editable e){}});
            wifiEnabled.setOnCheckedChangeListener((b,on)->wifiDraft=true);cellular.setOnCheckedChangeListener((b,on)->wifiDraft=true);
            JSONObject current=info.optJSONObject("current");hint(c,"当前 Wi-Fi："+(current==null||current.isNull("ssid")?"暂不可用":current.optString("ssid")));
            button(c,"添加当前 Wi-Fi",false,()->task("正在读取当前 Wi-Fi…",()->module.wifi(),fresh->{JSONObject network=fresh.optJSONObject("current");if(network==null||network.isNull("ssid"))throw new Exception("当前没有可识别的 Wi-Fi");String ssid=network.getString("ssid");JSONArray names=parseNames(wifiNames.getText().toString());boolean exists=false;for(int i=0;i<names.length();i++)exists|=ssid.equals(names.getString(i));if(!exists)names.put(ssid);wifiNames.setText(join(names));wifiDraft=true;}));
            button(c,"保存网络策略",true,()->{
                try {
                    JSONObject save=new JSONObject(wifiConfig.toString()).put(mode,parseNames(wifiNames.getText().toString())).put("mode",mode).put("enabled",wifiEnabled.isChecked()).put("proxy_on_non_wifi",cellular.isChecked());
                    task("正在保存网络策略…",()->{module.saveWifi(save);return save;},saved->{wifiConfig=saved;wifiDraft=false;Toast.makeText(this,"已保存，策略将在数秒内生效",Toast.LENGTH_LONG).show();});
                }catch(Exception e){error(e);}
            });
        });
    }
    static JSONArray parseNames(String text) throws Exception {
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        for(String name:text.split("\\r?\\n",-1))if(!name.isEmpty()){
            if(name.getBytes(StandardCharsets.UTF_8).length>32)throw new Exception("每个 Wi-Fi 名称最多 32 个 UTF-8 字节");
            for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw new Exception("Wi-Fi 名称不能包含控制字符");
            unique.add(name);
        }
        if(unique.size()>128)throw new Exception("每份名单最多 128 项");return new JSONArray(unique);
    }
    private String join(JSONArray values){if(values==null)return "";StringBuilder out=new StringBuilder();for(int i=0;i<values.length();i++){if(i>0)out.append('\n');out.append(values.optString(i));}return out.toString();}
    private void settings() {
        workspaceEntries();
        LinearLayout look=card("外观");
        hint(look,"云雾浅色与星夜深色，也可以随系统自动切换。");
        button(look,Appearance.LABELS[Appearance.selected(this)],false,()->new AlertDialog.Builder(this)
            .setTitle("选择外观").setSingleChoiceItems(Appearance.LABELS,Appearance.selected(this),(dialog,index)->{
                dialog.dismiss(); if(index!=Appearance.selected(this)){Appearance.save(this,index);recreate();}
            }).setNegativeButton("取消",null).show());
        LinearLayout service=card("模块服务");hint(service,"暂停代理会保留管理功能；停止服务会关闭整个代理后台。");
        paired(service,"启动服务",()->serviceCommand("start"),"重启服务",()->confirm("重启服务","代理连接会短暂中断，配置保留。",()->serviceCommand("restart")));
        button(service,"停止服务",false,()->confirm("停止服务","管理接口与手机代理将停止，之后可从 App 再次启动。",()->serviceCommand("stop"))).setTextColor(appearance.danger);
        LinearLayout options=card("兼容与自启");
        option(options,"开机启动",status!=null&&status.optBoolean("autostart"),"autostart");
        option(options,"Google 服务联网兼容",status!=null&&status.optBoolean("googleNetworkCompat"),"google-network");
        hint(options,"Google 兼容只适用于已验证的 ColorOS 框架，保留原有选项。");
        LinearLayout tools=card("维护工具");
        button(tools,"应用当前配置",false,()->task("正在应用配置…",()->api.post("runtime/reload",new JSONObject()),j->{Toast.makeText(this,"配置已应用",Toast.LENGTH_SHORT).show();refreshStatus(false);}));
        button(tools,"备份配置与名单",false,()->task("正在一致性备份，服务会短暂重启…",()->module.command("backup"),value->{
            String path="";for(String line:value.split("\\r?\\n"))if(line.startsWith("/data/adb/daenext/backups/")&&line.endsWith(".tar.gz"))path=line;
            textDialog("备份完成",path.isEmpty()?"备份操作完成，请在模块备份目录核对文件。":"备份已保存在：\n"+path);
        }));
        button(tools,"查看运行日志",false,()->task("正在读取日志…",()->module.command("logs"),value->textDialog("运行日志 · 仅在本机显示",value)));
        button(tools,"高级管理面板",false,()->panel("/"));
        hint(card("DaedNext Manager · 0.1.3"),"需要 KernelSU Root 授权和 alpha7+ 模块。界面视觉参考 Opal，管理交互参考 NetProxy；保留你的账户与配置，不上传设备数据。");
    }
    private void option(LinearLayout parent,String title,boolean checked,String command) {
        Switch option=new Switch(this);option.setText(title);option.setTextColor(ink);option.setChecked(checked);option.setPadding(0,dp(10),0,dp(10));parent.addView(option);actions.add(option);
        option.setEnabled(status!=null&&!busy);
        option.setOnCheckedChangeListener((button,on)->task("正在保存设置…",()->module.command(command+(on?" on":" off")),s->refreshStatus(false)));
    }
    private void serviceCommand(String command) { task("正在"+(command.equals("stop")?"停止":"启动")+"服务…",()->{String result=module.command(command);api.clear();return module.status();},s->{status=s;showPage(4);}); }
    private void confirm(String title,String message,Runnable yes) {new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->yes.run()).show();}
    private void textDialog(String title,String value) {ScrollView scroll=new ScrollView(this);TextView t=text(value,12,ink);t.setPadding(dp(18),dp(12),dp(18),dp(16));t.setTextIsSelectable(true);t.setTypeface(Typeface.MONOSPACE);scroll.addView(t);new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();}
}
