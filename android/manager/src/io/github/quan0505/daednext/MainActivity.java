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
    private TextView pageTitle, notice, badge;
    private GlassGooseView connectionArt;
    private TextView heroTitle,heroDetail,networkName,networkPolicy,downloadValue,uploadValue,trafficScope;
    private Button proxyToggle;
    private boolean proxySwitchPending,proxySwitchTarget;
    private AlertDialog formDialog;
    private JSONObject status, wifiConfig, metrics;
    private String mode = "blacklist";
    private EditText wifiNames;
    private Switch wifiEnabled, cellular;
    private final List<View> actions = new ArrayList<>();
    private boolean busy, foreground, reading, wifiDraft;
    private int page, generation, returnPage;
    private long detailGroupId;
    private JSONObject detailSection;
    private String detailKind;
    private JSONArray resourceGroups=new JSONArray();
    private Appearance appearance;
    private int ink, muted, background, card, border, accent;
    private final String[] pages = {"仪表盘", "节点", "订阅", "网络", "设置", "配置", "群组", "DNS", "路由", "资源"};
    private static final int[] RESOURCE_PAGES={5,7,8,6,1,2};
    private static final String[] RESOURCE_NAMES={"配置","DNS","路由","群组","节点","订阅"};
    private static final String[] RESOURCE_HINTS={"运行参数与探测","上游解析与查询分流","规则顺序与流量出口","候选节点与优选策略","独立与订阅节点","订阅来源与自动更新"};
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
        badge=text("KernelSU",10,muted);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(shape(card,20));brandRow.addView(badge);header.addView(brandRow);
        badge.setOnClickListener(v->{if(page>=5||page==1||page==2)onBackPressed();});
        pageTitle = text("你的连接", 31, ink); pageTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,-2);titleParams.topMargin=dp(15);header.addView(pageTitle,titleParams);
        root.addView(header);
        notice = text("正在连接本机服务…", 11, muted); notice.setPadding(dp(24), dp(2), dp(24), dp(6)); root.addView(notice);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = column(); content.setPadding(dp(20), dp(4), dp(20), dp(16)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = row(); nav.setPadding(dp(5), dp(7), dp(5), dp(7)); nav.setBackground(shape(appearance.nav,28));
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,-2);navParams.setMargins(dp(18),dp(5),dp(18),dp(12));
        returnPage=saved==null?0:saved.getInt("returnPage",0);
        root.addView(nav,navParams); buildNav(); showPage(saved==null?0:saved.getInt("page",0));
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("page",page);out.putInt("returnPage",returnPage); super.onSaveInstanceState(out); }
    @Override public void onBackPressed() {
        if(busy)return;
        if(page==9){showPage(0);return;}
        if(page==1||page==2){showPage(9);return;}
        if(page==6&&detailGroupId>0){showPage(6);return;}
        if((page==7||page==8)&&detailSection!=null){showPage(page);return;}
        if(page>=5){showPage(returnPage);return;}
        super.onBackPressed();
    }
    @Override protected void onResume() { super.onResume(); foreground = true; main.removeCallbacks(ticker); main.post(ticker); }
    @Override protected void onPause() { foreground = false; main.removeCallbacks(ticker);if(connectionArt!=null)connectionArt.stopMotion(); super.onPause(); }
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
        int[] destinations={0,9,3,4},icons={0,8,3,4};String[] labels={"首页","资源","网络","设置"};
        for (int i=0;i<destinations.length;i++) {
            final int index=destinations[i];boolean selected=index==page||(index==9&&(page==1||page==2||page>=5));LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(3),0,dp(3));
            android.widget.FrameLayout iconFrame=new android.widget.FrameLayout(this);
            if(selected)iconFrame.setBackground(shape(appearance.selected,13));
            NavIconView icon=new NavIconView(this,icons[i],selected?accent:muted);
            android.widget.FrameLayout.LayoutParams iconParams=new android.widget.FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER);iconFrame.addView(icon,iconParams);
            item.addView(iconFrame,new LinearLayout.LayoutParams(dp(42),dp(31)));
            TextView label=text(labels[i],10,selected?ink:muted);
            label.setGravity(Gravity.CENTER);
            item.addView(label,new LinearLayout.LayoutParams(-1,-2));
            item.setContentDescription(labels[i]); item.setOnClickListener(v -> {
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
        detailGroupId=0;detailSection=null;detailKind=null;badge.setText(page>=5||page==1||page==2?"‹ 返回":"KernelSU");
        if (page==0) dashboard();
        else if (page==1) loadNodes();
        else if (page==2) loadSubscriptions();
        else if (page==3) loadWifi();
        else if(page==4)settings();
        else if(page==5)loadConfigs();
        else if(page==6)loadGroups();
        else if(page==7||page==8)loadSections(page==7?"dns":"routings");
        else loadResourceHub();
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
                if(problem!=null) { if(proxySwitchPending){proxySwitchPending=false;if(connectionArt!=null)connectionArt.commit(ready().optBoolean("runtimeRequired"),ready().optBoolean("networkPolicyPaused"));updateDashboard();}error(problem); return; }
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
                if(proxySwitchPending)return;
                if(problem!=null){ notice.setText(problem.getMessage()); notice.setTextColor(appearance.danger); if(force)error(problem); return; }
                boolean firstStatus=status==null;
                status=result;if(selectedPage==0)metrics=statistics;
                notice.setText(serviceOn()?"本机服务已连接":"后台服务未启动");notice.setTextColor(muted);
                if(page==0 && epoch==generation && !busy)updateDashboard();
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
        connectionArt=new GlassGooseView(this,wanted,bypass,appearance.light);connectionArt.setMotionFinished(this::updateDashboard);
        hero.addView(connectionArt,new LinearLayout.LayoutParams(-1,dp(190)));
        String state=status==null?"等待连接":!serviceOn()?"服务未启动":bypass&&wanted?"已自动直连":active?"代理运行中":wanted?"正在恢复连接":"代理已暂停";
        heroTitle=text(state,29,ink);heroTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heroTitle.setGravity(Gravity.CENTER);hero.addView(heroTitle);
        heroDetail=text("",13,muted);heroDetail.setMinLines(2);heroDetail.setGravity(Gravity.CENTER);LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);detailParams.topMargin=dp(6);detailParams.bottomMargin=dp(10);hero.addView(heroDetail,detailParams);
        proxyToggle=button(hero,wanted?"暂停代理":"启用代理",true,this::toggleProxy);
        LinearLayout network=card("");JSONObject w=status==null?null:status.optJSONObject("wifiPolicy");
        LinearLayout networkRow=row();network.addView(networkRow);
        android.widget.FrameLayout wifiIcon=new android.widget.FrameLayout(this);wifiIcon.setBackground(shape(appearance.control,15));
        wifiIcon.addView(new NavIconView(this,3,accent),new android.widget.FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER));
        networkRow.addView(wifiIcon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout networkText=column();LinearLayout.LayoutParams networkParams=new LinearLayout.LayoutParams(0,-2,1);networkParams.leftMargin=dp(13);networkRow.addView(networkText,networkParams);
        String iface=status==null?"":status.optString("interface");
        String name=w!=null&&!w.isNull("ssid")?w.optString("ssid"):iface.startsWith("wlan")?"Wi-Fi":iface.startsWith("rmnet")?"移动数据":"当前网络";
        networkName=text(name,18,ink);networkText.addView(networkName);networkPolicy=text("",13,muted);networkText.addView(networkPolicy);
        TextView chevron=text("›",25,muted);networkRow.addView(chevron);network.setContentDescription("网络策略");
        network.setOnClickListener(v->{if(!busy)showPage(3);});actions.add(network);

        workspaceEntries();

        LinearLayout rates=row();LinearLayout.LayoutParams ratesParams=new LinearLayout.LayoutParams(-1,-2);ratesParams.bottomMargin=dp(12);content.addView(rates,ratesParams);
        downloadValue=metric(rates,"下载速度","—",appearance.light?0xff405d97:0xffb8c8ff,true);
        uploadValue=metric(rates,"上传速度","—",appearance.light?0xff795399:0xffd0b7fb,false);
        trafficScope=text("",10,muted);trafficScope.setPadding(dp(3),0,dp(3),dp(12));content.addView(trafficScope);

        LinearLayout advanced=card("");LinearLayout advancedRow=row();advanced.addView(advancedRow);
        LinearLayout advancedText=column();advancedRow.addView(advancedText,new LinearLayout.LayoutParams(0,-2,1));
        advancedText.addView(text("高级管理面板",16,ink));hint(advancedText,"策略组、DNS 与路由规则");advancedRow.addView(text("↗",22,muted));
        advanced.setOnClickListener(v->{if(!busy)panel("/");});actions.add(advanced);
        button(content,"查看运行详情",false,()->task("正在读取运行概况…",()->api.get("runtime/overview"),this::showMetrics));
        if(status==null)button(content,"连接模块 / 申请 Root 授权",false,()->refreshStatus(true));
        updateDashboard();
    }
    private void updateDashboard(){
        if(page!=0||connectionArt==null||heroTitle==null)return;
        JSONObject r=ready(),w=status==null?null:status.optJSONObject("wifiPolicy");boolean wanted=r.optBoolean("runtimeRequired"),active=r.optBoolean("runtimeRunning"),bypass=r.optBoolean("networkPolicyPaused");
        connectionArt.sync(wanted,bypass);
        heroTitle.setText(proxySwitchPending?(proxySwitchTarget?"正在启用代理":"正在暂停代理"):status==null?"等待连接":!serviceOn()?"服务未启动":bypass&&wanted?"已自动直连":active?"代理运行中":wanted?"正在恢复连接":"代理已暂停");
        heroDetail.setText(proxySwitchPending?"正在与本机服务同步…":bypass&&wanted?"当前 Wi-Fi 使用上游网络\n离开后，手机代理会自动接管。":active?"连接已由 DaedNext 接管\n按你的规则，自由切换出口。":"保留你的节点与规则\n启用后，按网络策略自动连接。");
        proxyToggle.setText(proxySwitchPending?"正在切换…":wanted?"暂停代理":"启用代理");proxyToggle.setEnabled(!busy&&!proxySwitchPending&&!connectionArt.isAnimating()&&status!=null);
        String iface=status==null?"":status.optString("interface");networkName.setText(w!=null&&!w.isNull("ssid")?w.optString("ssid"):iface.startsWith("wlan")?"Wi-Fi":iface.startsWith("rmnet")?"移动数据":"当前网络");
        networkPolicy.setText(w==null?"等待网络策略":w.optBoolean("enabled")?(w.optString("mode").equals("blacklist")?"黑名单":"白名单")+" · "+(w.optBoolean("direct")?"手机直连":"使用代理"):"按现有规则连接");
        downloadValue.setText(metrics==null?"—":bytes(metrics.optDouble("downloadRate"))+"/s");uploadValue.setText(metrics==null?"—":bytes(metrics.optDouble("uploadRate"))+"/s");
        trafficScope.setText(bypass&&wanted?"直连期间，Wi-Fi 上游流量不计入这里。":"仅统计由手机代理转发的流量。");
    }
    private void toggleProxy(){
        if(busy||proxySwitchPending||connectionArt==null||connectionArt.isAnimating()||status==null)return;
        boolean enable=!ready().optBoolean("runtimeRequired");proxySwitchPending=true;proxySwitchTarget=enable;connectionArt.beginSwitch(enable);updateDashboard();
        task(enable?"正在启用代理…":"正在暂停代理…",()->{
            JSONObject result=new JSONObject();
            try{if(!serviceOn())module.command("start");api.post(enable?"runtime/reload":"runtime/stop",new JSONObject());result.put("status",module.status());}
            catch(Exception problem){result.put("error",problem.getMessage()==null?"切换失败":problem.getMessage());try{result.put("status",module.status());}catch(Exception ignored){}}
            return result;
        },result->{
            status=result.optJSONObject("status");proxySwitchPending=false;
            connectionArt.commit(ready().optBoolean("runtimeRequired"),ready().optBoolean("networkPolicyPaused"));updateDashboard();
            if(result.has("error")){error(new Exception(result.getString("error")+(status==null?"；当前状态待确认，请刷新后查看":"")));if(status==null)refreshStatus(false);}
        });
    }
    private TextView metric(LinearLayout parent,String label,String value,int color,boolean first){
        LinearLayout box=column();box.setPadding(dp(17),dp(16),dp(12),dp(16));box.setBackground(shape(card,22));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(first)params.rightMargin=dp(5);else params.leftMargin=dp(5);parent.addView(box,params);
        box.addView(text(label,11,muted));TextView number=text(value,22,color);number.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));box.addView(number);return number;
    }
    private void showMetrics(JSONObject j){
        new AlertDialog.Builder(this).setTitle("运行概况").setMessage("上传速度："+bytes(j.optDouble("uploadRate"))+"/s\n下载速度："+bytes(j.optDouble("downloadRate"))+"/s\n累计上传："+bytes(j.optDouble("uploadTotal"))+"\n累计下载："+bytes(j.optDouble("downloadTotal"))+"\n内存："+bytes(j.optDouble("rssBytes"))+"\nCPU："+String.format(Locale.ROOT,"%.1f%%",j.optDouble("cpuUsagePercent",0))).setPositiveButton("完成",null).show();
    }
    private static String bytes(double n) { if(Double.isNaN(n)||n<0)return "—"; if(n<1024)return String.format(Locale.ROOT,"%.0f B",n);if(n<1048576)return String.format(Locale.ROOT,"%.1f KB",n/1024);if(n<1073741824)return String.format(Locale.ROOT,"%.1f MB",n/1048576);return String.format(Locale.ROOT,"%.2f GB",n/1073741824); }
    private void panel(String path) { Intent intent=new Intent(this,PanelActivity.class);intent.putExtra("path",path);startActivity(intent); }
    private void workspaceEntries() {
        LinearLayout entries=card("资源工作台");hint(entries,"把连接的每一层，都安排妥当。");
        for(int i=0;i<RESOURCE_NAMES.length;i+=2){final int left=i,right=i+1;LinearLayout line=row();entries.addView(line);
            resourceTile(line,left,()->openWorkspace(RESOURCE_PAGES[left]));resourceTile(line,right,()->openWorkspace(RESOURCE_PAGES[right]));}
    }
    private void openWorkspace(int target){returnPage=page==9?9:page<5?page:9;showPage(target);}
    private int resourceIcon(int index){return new int[]{4,5,6,7,1,2}[index];}
    private int resourceColor(int index){return appearance.light?new int[]{0xff6352a3,0xff3e7d78,0xff486e9f,0xff805d86,0xff6070a3,0xff91734a}[index]:new int[]{0xffb4aeff,0xff8fcac1,0xff9bb8e5,0xffd1b0d8,0xffabb5e5,0xffdcc394}[index];}
    private void resourceTile(LinearLayout line,int index,Runnable action){
        LinearLayout tile=row();tile.setPadding(dp(12),dp(11),dp(12),dp(11));tile.setBackground(shape(appearance.control,18));
        tile.addView(new NavIconView(this,resourceIcon(index),resourceColor(index)),new LinearLayout.LayoutParams(dp(20),dp(20)));
        TextView title=text(RESOURCE_NAMES[index],14,ink);LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(-2,-2);textParams.leftMargin=dp(9);tile.addView(title,textParams);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(49),1);p.topMargin=dp(9);p.setMarginEnd(dp(index%2==0?5:0));p.setMarginStart(dp(index%2==0?0:5));line.addView(tile,p);
        tile.setContentDescription(RESOURCE_NAMES[index]);tile.setOnClickListener(v->{if(!busy)action.run();});actions.add(tile);
    }
    private void resourceHeading(int index,String note){
        LinearLayout c=card("");LinearLayout title=row();c.addView(title);
        title.addView(new NavIconView(this,resourceIcon(index),resourceColor(index)),new LinearLayout.LayoutParams(dp(27),dp(27)));
        TextView name=text(RESOURCE_NAMES[index]+"管理",22,ink);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(12);title.addView(name,p);hint(c,note);
    }
    private void loadResourceHub(){
        TextView introduction=text("配置、解析与出口，一处管理。",13,muted);introduction.setPadding(dp(3),dp(1),dp(3),dp(18));content.addView(introduction);
        LinearLayout gridRow=null;
        for(int i=0;i<RESOURCE_NAMES.length;i++){
            if(i%2==0){gridRow=row();gridRow.setGravity(Gravity.TOP);content.addView(gridRow);}
            final int index=i;LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(14));c.setBackground(shape(card,24));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(148),1);cp.bottomMargin=dp(12);if(i%2==0)cp.rightMargin=dp(6);else cp.leftMargin=dp(6);gridRow.addView(c,cp);
            LinearLayout line=row();c.addView(line);
            FrameLayout symbol=new FrameLayout(this);symbol.setBackground(shape(appearance.control,15));symbol.addView(new NavIconView(this,resourceIcon(i),resourceColor(i)),new FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER));line.addView(symbol,new LinearLayout.LayoutParams(dp(42),dp(42)));
            TextView count=text("—",13,muted);count.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);count.setTag("resource-count-"+i);line.addView(count,new LinearLayout.LayoutParams(0,-2,1));
            TextView title=text(RESOURCE_NAMES[i]+"  ›",18,ink);title.setPadding(0,dp(13),0,dp(4));c.addView(title);c.addView(text(RESOURCE_HINTS[i],11,muted));
            c.setContentDescription(RESOURCE_NAMES[i]);c.setOnClickListener(v->{if(!busy)openWorkspace(RESOURCE_PAGES[index]);});actions.add(c);
        }
        task("正在读取资源概况…",()->{
            String[] endpoints={"configs?summary=true","dns?summary=true","routings?summary=true","groups?summary=true","nodes","subscriptions"};int[] counts=new int[6];
            for(int i=0;i<endpoints.length;i++)counts[i]=items(api.get(endpoints[i])).length();counts[4]+=items(api.get("nodes?independent=false")).length();return counts;
        },counts->{for(int i=0;i<counts.length;i++){TextView view=content.findViewWithTag("resource-count-"+i);if(view!=null)view.setText(String.format(Locale.ROOT,"%02d",counts[i]));}});
    }
    private JSONArray items(JSONObject j) { JSONArray a=j.optJSONArray("items");return a==null?new JSONArray():a; }
    private void loadNodes() {
        resourceHeading(4,"查看协议与延迟，管理独立和订阅节点。");
        LinearLayout tools=card("");hint(tools,"导入节点、查看延迟，使用策略组管理代理出口。");
        paired(tools,"导入节点链接",this::importNodes,"策略组",()->openWorkspace(6));
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
            hint(card("共 "+all.length()+" 个节点"),"在群组页管理节点归属与自动优选策略。");
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
        resourceHeading(5,"让节点来源井然有序，更新与管理更从容。");
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
        hint(card("DaedNext Manager · 0.1.8"),"需要 KernelSU Root 授权和 alpha7+ 模块。界面视觉参考 Opal，管理交互参考 NetProxy；保留你的账户与配置，不上传设备数据。");
    }
    // Native configuration workspace. Saving never implicitly reloads the runtime.
    private void loadConfigs() {
        resourceHeading(0,"一份配置，掌握连接的基础行为。");
        LinearLayout top=card("运行参数");hint(top,"编辑核心参数和连通性探测。保存后，点击“应用当前配置”使修改生效。");
        paired(top,"新建配置",()->task("正在准备配置…",()->api.get("configs"),data->{
            JSONArray list=items(data);JSONObject template=null;
            for(int i=0;i<list.length();i++)if(list.getJSONObject(i).optBoolean("selected"))template=list.getJSONObject(i);
            if(template==null&&list.length()>0)template=list.getJSONObject(0);
            if(template==null)task("正在读取默认值…",()->api.post("configs/parsed",new JSONObject()),j->configForm(null,j.optJSONObject("parsedGlobal")));
            else configForm(null,template.optJSONObject("parsedGlobal"));
        }),"刷新",()->showPage(5));
        button(top,"应用当前配置",true,()->confirm("应用当前配置","将应用已选配置与群组设置，代理连接可能短暂重建。",()->task("正在应用配置…",()->api.post("runtime/reload",new JSONObject()),j->{Toast.makeText(this,"配置已应用",Toast.LENGTH_SHORT).show();refreshStatus(false);})));
        task("正在读取配置…",()->api.get("configs"),result->{
            JSONArray list=items(result);
            if(list.length()==0)hint(card("还没有配置"),"新建一份配置后，选择并应用。");
            for(int i=0;i<list.length();i++){
                JSONObject config=list.getJSONObject(i),values=config.optJSONObject("parsedGlobal");
                LinearLayout c=card(config.optString("name","未命名配置"));
                TextView state=text(config.optBoolean("selected")?"● 已选配置":"备用配置",12,config.optBoolean("selected")?accent:muted);c.addView(state);
                if(values!=null){
                    hint(c,"检查间隔  "+values.optString("checkInterval","—")+"    拨号模式  "+values.optString("dialMode","—"));
                    hint(c,"日志级别  "+values.optString("logLevel","—"));
                }else hint(c,"解析异常："+config.optString("parseError","无法读取结构化参数"));
                paired(c,"编辑参数",()->task("正在读取参数…",()->api.get("configs/"+config.optLong("id")),fresh->configForm(fresh,fresh.optJSONObject("parsedGlobal"))),
                    "更多",()->new AlertDialog.Builder(this).setTitle(config.optString("name"))
                        .setItems(new String[]{"选择此配置","复制配置","删除配置"},(dialog,index)->{
                            if(index==0)task("正在选择配置…",()->api.post("configs/"+config.optLong("id")+"/select",new JSONObject()),j->{showPage(5);Toast.makeText(this,"已选择，应用后生效",Toast.LENGTH_LONG).show();});
                            else if(index==1){try{configForm(null,values);}catch(Exception e){error(e);}}
                            else if(config.optBoolean("selected"))error(new Exception("当前配置已被选中，请先选择另一份配置后再删除"));
                            else confirm("删除配置","将删除“"+config.optString("name")+"”。正在运行使用的配置无法删除。",()->task("正在删除配置…",()->api.delete("configs/"+config.optLong("id")),j->showPage(5)));
                        }).show());
            }
        });
    }
    private Spinner choices(LinearLayout parent,String label,String value,String[] values){
        hint(parent,label);ArrayList<String> options=new ArrayList<>(Arrays.asList(values));
        if(!options.contains(value))options.add(value);
        Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));
        spinner.setSelection(options.indexOf(value));parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));return spinner;
    }
    private void formHeading(LinearLayout parent,String title){TextView heading=text(title,17,accent);heading.setPadding(0,dp(16),0,dp(8));parent.addView(heading);}
    private void configForm(JSONObject existing,JSONObject source) throws Exception {
        if(source==null)throw new Exception("配置解析失败，请先检查原配置；未修改任何参数");
        JSONObject original=new JSONObject(source.toString());LinearLayout fields=formFields();
        EditText name=field(fields,"配置名称",existing==null?"新配置":existing.optString("name"),false,false);
        Map<String,EditText> inputs=new LinkedHashMap<>();Map<String,Switch> toggles=new LinkedHashMap<>();
        Map<String,Spinner> selects=new LinkedHashMap<>();
        String[][] definitions={
            {"连接与探测","checkInterval","检查间隔（如 50s）","checkTolerance","切换容差（如 50ms）","tcpCheckUrl","TCP 探测地址 · 每行一项","udpCheckDns","UDP DNS 探测地址 · 每行一项","tcpCheckHttpMethod","HTTP 探测方法","sniffingTimeout","流量嗅探超时"},
            {"拨号与日志","dialMode","拨号模式","logLevel","日志级别","fallbackResolver","备用 DNS 地址","tlsImplementation","TLS 实现","utlsImitate","uTLS 客户端指纹","bandwidthMaxTx","最大上传带宽","bandwidthMaxRx","最大下载带宽"},
            {"接口与兼容","wanInterface","WAN 接口 · 每行一项","lanInterface","LAN 接口 · 每行一项","allowInsecure","允许不安全 TLS","tproxyPortProtect","保护透明代理端口","disableWaitingNetwork","不等待网络就绪","enableLocalTcpFastRedirect","本机 TCP 快速重定向","autoConfigKernelParameter","自动设置内核参数","mptcp","启用 MPTCP"}
        };
        for(String[] section:definitions){formHeading(fields,section[0]);for(int i=1;i<section.length;i+=2){
            String key=section[i],label=section[i+1];if(!original.has(key))continue;Object value=original.get(key);
            if(value instanceof Boolean){Switch toggle=new Switch(this);toggle.setText(label);toggle.setTextColor(ink);toggle.setChecked((Boolean)value);toggle.setPadding(0,dp(10),0,dp(10));fields.addView(toggle);toggles.put(key,toggle);}
            else if(key.equals("dialMode"))selects.put(key,choices(fields,label,value.toString(),new String[]{"domain+","domain","ip","domain++"}));
            else if(key.equals("logLevel"))selects.put(key,choices(fields,label,value.toString(),new String[]{"trace","debug","info","warn","error","fatal"}));
            else if(key.equals("tcpCheckHttpMethod"))selects.put(key,choices(fields,label,value.toString(),new String[]{"HEAD","GET"}));
            else inputs.put(key,field(fields,label,value instanceof JSONArray?join((JSONArray)value):value.toString(),value instanceof JSONArray,false));
        }}
        hint(fields,"透明代理端口、路由标记及其他参数沿用原值。保存不会自动切换当前连接。");
        form(existing==null?"新建配置":"编辑配置",fields,()->{
            try{
                String title=name.getText().toString().trim();if(title.isEmpty())throw new Exception("请填写配置名称");
                JSONObject edited=new JSONObject(original.toString());
                for(Map.Entry<String,EditText> entry:inputs.entrySet()){
                    String key=entry.getKey(),value=entry.getValue().getText().toString().trim();
                    if(original.get(key) instanceof JSONArray){JSONArray array=new JSONArray();for(String line:value.split("\\r?\\n"))if(!line.trim().isEmpty())array.put(line.trim());edited.put(key,array);}
                    else edited.put(key,value);
                }
                for(Map.Entry<String,Switch> entry:toggles.entrySet())edited.put(entry.getKey(),entry.getValue().isChecked());
                for(Map.Entry<String,Spinner> entry:selects.entrySet())edited.put(entry.getKey(),entry.getValue().getSelectedItem().toString());
                JSONObject body=new JSONObject().put("name",title).put("parsedGlobal",edited);
                task("正在校验并保存配置…",()->{
                    if(existing==null)return api.post("configs",body);
                    JSONObject latest=api.get("configs/"+existing.getLong("id"));
                    if(latest.optLong("version")!=existing.optLong("version"))throw new Exception("配置已被其他页面修改，请取消后刷新再编辑");
                    return api.patch("configs/"+existing.getLong("id"),body);
                },j->{showPage(5);Toast.makeText(this,"已保存，应用后生效",Toast.LENGTH_LONG).show();});
            }catch(Exception e){error(e);}
        });
    }

    // Native group workspace and per-membership changes preserve unrelated filters.
    private static final String[] POLICIES={"min_moving_avg","min_avg10","min","random","fixed"};
    private static final String[] POLICY_LABELS={"平滑延迟优选","最近十次平均优选","最低延迟","随机选择","固定单节点"};
    private String policyLabel(String key){for(int i=0;i<POLICIES.length;i++)if(POLICIES[i].equals(key))return POLICY_LABELS[i];return key;}
    private void loadGroups(){
        resourceHeading(3,"把候选节点组织成适合你的出口。");
        LinearLayout top=card("代理出口");hint(top,"为节点和订阅设置出口策略。编辑后在配置页应用，使修改生效。");
        paired(top,"新建群组",()->groupForm(null),"刷新",()->showPage(6));
        task("正在读取群组…",()->api.get("groups?summary=true"),result->{
            JSONArray groups=items(result);if(groups.length()==0)hint(card("还没有群组"),"新建群组，再添加节点或订阅。");
            for(int i=0;i<groups.length();i++){
                JSONObject group=groups.getJSONObject(i);LinearLayout c=card(group.optString("name","未命名群组"));
                c.addView(text(policyLabel(group.optString("policy")),14,accent));
                hint(c,group.optInt("nodeCount")+" 个直接节点  ·  "+group.optInt("subscriptionCount")+" 个订阅  ·  "+group.optInt("materializedCandidateCount")+" 个候选");
                JSONObject selected=group.optJSONObject("runtimeSelectedNode"),best=group.optJSONObject("bestNode");
                if(selected!=null)hint(c,"运行时出口  "+selected.optString("name","—"));
                else if(best!=null)hint(c,"最近探测优选  "+best.optString("name","—"));
                paired(c,"管理成员",()->groupDetails(group.optLong("id")),"编辑策略",()->task("正在读取策略…",()->api.get("groups/"+group.optLong("id")),this::groupForm));
            }
        });
    }
    private void groupForm(JSONObject group){
        LinearLayout fields=formFields();EditText name=field(fields,"群组名称",group==null?"":group.optString("name"),false,false);
        hint(fields,"出口选择策略");Spinner policy=new Spinner(this);policy.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,POLICY_LABELS));
        int selected=0;for(int i=0;group!=null&&i<POLICIES.length;i++)if(POLICIES[i].equals(group.optString("policy")))selected=i;
        policy.setSelection(selected);fields.addView(policy,new LinearLayout.LayoutParams(-1,dp(48)));
        hint(fields,"固定策略只允许一个候选节点；自动优选依据探测结果选择出口。");
        if(group!=null)hint(fields,"修改名称后，引用此群组的路由规则也需要同步。");
        JSONArray params=group==null?new JSONArray():group.optJSONArray("policyParams");StringBuilder existing=new StringBuilder();
        if(params!=null)for(int i=0;i<params.length();i++){JSONObject param=params.optJSONObject(i);if(param!=null){if(existing.length()>0)existing.append('\n');existing.append(param.optString("key")).append('=').append(param.optString("val"));}}
        EditText options=field(fields,"可选策略参数 · 每行 key=value",existing.toString(),true,false);
        form(group==null?"新建群组":"编辑群组",fields,()->{
            try{
                String title=name.getText().toString().trim();if(!title.matches("[\\p{L}_][\\p{L}\\p{N}_-]*"))throw new Exception("名称可使用中英文字、数字、下划线或连字符，以文字或下划线开头");
                JSONArray values=new JSONArray();HashSet<String> keys=new HashSet<>();
                for(String line:options.getText().toString().split("\\r?\\n"))if(!line.trim().isEmpty()){
                    int split=line.indexOf('=');if(split<1)throw new Exception("策略参数请按 key=value 填写");
                    String key=line.substring(0,split).trim();if(!keys.add(key))throw new Exception("策略参数不能重复："+key);
                    values.put(new JSONObject().put("key",key).put("val",line.substring(split+1).trim()));
                }
                JSONObject body=new JSONObject().put("name",title).put("policy",POLICIES[policy.getSelectedItemPosition()]).put("policyParams",values);
                task("正在保存群组…",()->{
                    if(group==null)return api.post("groups",body);
                    JSONObject latest=api.get("groups/"+group.getLong("id"));
                    if(latest.optLong("version")!=group.optLong("version"))throw new Exception("群组已被修改，请取消后刷新再编辑");
                    return api.patch("groups/"+group.getLong("id"),body);
                },j->{showPage(6);Toast.makeText(this,"已保存，应用后生效",Toast.LENGTH_LONG).show();});
            }catch(Exception e){error(e);}
        });
    }
    private void groupDetails(long id){
        task("正在读取群组成员…",()->api.get("groups/"+id),group->{
            content.removeAllViews();actions.clear();generation++;detailGroupId=id;pageTitle.setText(group.optString("name","群组"));
            LinearLayout top=card("群组策略");top.addView(text(policyLabel(group.optString("policy")),17,accent));
            hint(top,"成员变更保存到群组，应用配置后更新代理出口。");paired(top,"编辑策略",()->groupForm(group),"返回群组",()->showPage(6));
            LinearLayout nodes=card("直接节点");JSONArray nodeList=group.optJSONArray("nodes");
            hint(nodes,(nodeList==null?0:nodeList.length())+" 个节点，独立于订阅筛选");button(nodes,"添加节点",false,()->pickGroupNodes(group));
            if(nodeList!=null)for(int i=0;i<nodeList.length();i++){
                JSONObject node=nodeList.getJSONObject(i);LinearLayout c=card(node.optString("name","未命名节点"));hint(c,node.optString("protocol").toUpperCase(Locale.ROOT));
                button(c,"移出群组",false,()->confirm("移出节点","只移出当前群组，保留节点本身。",()->task("正在移出节点…",()->api.delete("groups/"+id+"/nodes",new JSONObject().put("nodeIds",new JSONArray().put(node.getLong("id")))),j->groupDetails(id)))).setTextColor(appearance.danger);
            }
            LinearLayout subs=card("订阅成员");JSONArray subscriptions=group.optJSONArray("subscriptions");
            hint(subs,(subscriptions==null?0:subscriptions.length())+" 个订阅，每份订阅可单独筛选节点");button(subs,"添加订阅",false,()->pickGroupSubscription(id));
            if(subscriptions!=null)for(int i=0;i<subscriptions.length();i++){
                JSONObject subscription=subscriptions.getJSONObject(i);LinearLayout c=card(subscription.optString("tag","未命名订阅"));
                String filter=subscription.isNull("nameFilterRegex")?"":subscription.optString("nameFilterRegex");hint(c,"筛选  "+(filter.isEmpty()?"全部节点":filter));hint(c,"匹配 "+subscription.optInt("matchedCount")+" 个节点");
                paired(c,"筛选与节点",()->subscriptionMembership(id,subscription),"移出订阅",()->confirm("移出订阅","只解除与当前群组的关联，保留订阅和节点。",()->task("正在移出订阅…",()->api.delete("groups/"+id+"/subscriptions",new JSONObject().put("subscriptionIds",new JSONArray().put(subscription.getLong("subscriptionId")))),j->groupDetails(id))))[1].setTextColor(appearance.danger);
            }
            button(content,"删除群组",false,()->confirm("删除群组","将删除群组及其成员关联，节点和订阅仍保留。正在运行使用的群组无法删除。",()->task("正在删除群组…",()->api.delete("groups/"+id),j->showPage(6)))).setTextColor(appearance.danger);
            scroll.post(()->scroll.scrollTo(0,0));
        });
    }
    private void pickGroupNodes(JSONObject group){
        task("正在读取可用节点…",()->{
            JSONArray all=items(api.get("nodes")),subscribed=items(api.get("nodes?independent=false"));for(int i=0;i<subscribed.length();i++)all.put(subscribed.get(i));return all;
        },all->{
            HashSet<Long> attached=new HashSet<>();JSONArray current=group.optJSONArray("nodes");if(current!=null)for(int i=0;i<current.length();i++)attached.add(current.getJSONObject(i).getLong("id"));
            ArrayList<JSONObject> available=new ArrayList<>();for(int i=0;i<all.length();i++)if(!attached.contains(all.getJSONObject(i).getLong("id")))available.add(all.getJSONObject(i));
            if(available.isEmpty()){Toast.makeText(this,"没有可添加的节点",Toast.LENGTH_SHORT).show();return;}
            String[] labels=new String[available.size()];boolean[] checked=new boolean[labels.length];for(int i=0;i<labels.length;i++)labels[i]=available.get(i).optString("name","未命名节点");
            AlertDialog picker=new AlertDialog.Builder(this).setTitle("添加节点").setMultiChoiceItems(labels,checked,(d,index,on)->checked[index]=on).setNegativeButton("取消",null).setPositiveButton("添加",null).create();
            picker.setOnShowListener(d->picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(busy)return;JSONArray ids=new JSONArray();for(int i=0;i<checked.length;i++)if(checked[i])ids.put(available.get(i).optLong("id"));
                if(ids.length()==0){Toast.makeText(this,"请至少选择一个节点",Toast.LENGTH_SHORT).show();return;}
                task("正在添加节点…",()->api.post("groups/"+group.getLong("id")+"/nodes",new JSONObject().put("nodeIds",ids)),j->{picker.dismiss();groupDetails(group.getLong("id"));});
            }));picker.show();
        });
    }
    private void pickGroupSubscription(long groupId){
        task("正在读取订阅…",()->api.get("subscriptions"),result->{
            JSONArray list=items(result);if(list.length()==0){Toast.makeText(this,"请先在订阅页添加订阅",Toast.LENGTH_SHORT).show();return;}
            String[] labels=new String[list.length()];for(int i=0;i<labels.length;i++)labels[i]=list.getJSONObject(i).optString("tag","未命名订阅");
            new AlertDialog.Builder(this).setTitle("选择订阅").setItems(labels,(d,index)->{
                try{
                    long id=list.getJSONObject(index).getLong("id");
                    task("正在读取订阅关联…",()->api.get("groups/"+groupId),group->{
                        JSONArray attached=group.optJSONArray("subscriptions");
                        if(attached!=null)for(int i=0;i<attached.length();i++){JSONObject sub=attached.getJSONObject(i);if(sub.optLong("subscriptionId")==id){subscriptionMembership(groupId,sub);return;}}
                        subscriptionMembership(groupId,new JSONObject().put("subscriptionId",id).put("tag",labels[index]));
                    });
                }catch(Exception e){error(e);}
            }).setNegativeButton("取消",null).show();
        });
    }
    private void subscriptionMembership(long groupId,JSONObject subscription){
        LinearLayout fields=formFields();hint(fields,subscription.optString("tag","订阅"));
        EditText filter=field(fields,"节点名称筛选 · 正则表达式，留空表示全部",subscription.isNull("nameFilterRegex")?"":subscription.optString("nameFilterRegex"),false,false);
        JSONArray matched=subscription.optJSONArray("matchedNodes");if(matched!=null){formHeading(fields,"当前匹配节点");for(int i=0;i<matched.length();i++)hint(fields,matched.optJSONObject(i).optString("name","未命名节点"));}
        form("订阅筛选",fields,()->{
            try{String expression=filter.getText().toString();JSONObject body=new JSONObject().put("subscriptionIds",new JSONArray().put(subscription.getLong("subscriptionId"))).put("nameFilterRegex",expression.isEmpty()?JSONObject.NULL:expression);
                task("正在保存订阅筛选…",()->api.post("groups/"+groupId+"/subscriptions",body),j->groupDetails(groupId));
            }catch(Exception e){error(e);}
        });
    }
    // DNS and routing share the resource shell, with native per-item editors.
    private int sectionPage(String kind){return kind.equals("dns")?7:8;}
    private String sectionKey(String kind){return kind.equals("dns")?"dns":"routing";}
    private void loadSections(String kind){
        boolean dns=kind.equals("dns");int target=sectionPage(kind);resourceHeading(dns?1:2,dns?"为每一次查询，安排合适的解析路径。":"从第一条规则到最终出口，清晰掌握流量去向。");
        LinearLayout controls=card(dns?"解析方案":"路由方案");hint(controls,"选择与编辑分开保存，应用配置后才更新当前连接。");
        paired(controls,dns?"新建 DNS":"新建路由",()->task("正在准备方案…",()->api.get(kind),result->{
            JSONArray list=items(result);JSONObject template=null;for(int i=0;i<list.length();i++)if(list.getJSONObject(i).optBoolean("selected"))template=list.getJSONObject(i);
            if(template==null&&list.length()>0)template=list.getJSONObject(0);sectionNameForm(kind,template,true);
        }),"刷新",()->showPage(target));
        task("正在读取方案…",()->api.get(kind),result->{
            JSONArray list=items(result);if(list.length()==0)hint(card("还没有方案"),"新建一份方案，再添加上游或规则。");
            for(int i=0;i<list.length();i++){
                JSONObject section=list.getJSONObject(i);LinearLayout c=card(section.optString("name","未命名方案"));
                c.addView(text(section.optBoolean("selected")?"● 已选方案":"备用方案",12,section.optBoolean("selected")?accent:muted));
                String raw=section.optString(sectionKey(kind));
                try{if(dns){String wrapped=ResourceText.wrap("dns",raw);ResourceText.Block area=ResourceText.path(wrapped,"dns","upstream");hint(c,(area==null?0:ResourceText.entries(wrapped,area.open+1,area.close).size())+" 个上游  ·  "+ResourceText.rules(wrapped,true,"request").size()+" 项查询规则");}
                    else hint(c,ResourceText.rules(raw,false,"").size()+" 项规则  ·  自上而下匹配");
                }catch(Exception e){hint(c,"自定义方案 · 可在完整文本中检查");}
                paired(c,dns?"管理解析":"管理规则",()->sectionDetails(kind,section.optLong("id")),"更多",()->sectionMenu(kind,section));
            }
        });
    }
    private void sectionMenu(String kind,JSONObject section){
        new AlertDialog.Builder(this).setTitle(section.optString("name")).setItems(new String[]{"选择此方案","复制方案","重命名","删除方案"},(d,index)->{
            if(index==0)task("正在选择方案…",()->api.post(kind+"/"+section.optLong("id")+"/select",new JSONObject()),j->{showPage(sectionPage(kind));Toast.makeText(this,"已选择，应用配置后生效",Toast.LENGTH_LONG).show();});
            else if(index==1)sectionNameForm(kind,section,true);
            else if(index==2)sectionNameForm(kind,section,false);
            else if(section.optBoolean("selected"))error(new Exception("请先选择其他方案，再删除当前方案"));
            else confirm("删除方案","将删除“"+section.optString("name")+"”。",()->task("正在删除方案…",()->api.delete(kind+"/"+section.optLong("id")),j->showPage(sectionPage(kind))));
        }).show();
    }
    private void sectionNameForm(String kind,JSONObject template,boolean copy){
        LinearLayout fields=formFields();EditText name=field(fields,"方案名称",copy?"新"+(kind.equals("dns")?"DNS":"路由")+"方案":template.optString("name"),false,false);
        if(copy)hint(fields,template==null?"创建后可添加上游与规则。":"以“"+template.optString("name")+"”为模板，原方案保持不变。");
        form(copy?"新建方案":"重命名",fields,()->{
            String title=name.getText().toString().trim();if(title.isEmpty()){error(new Exception("请填写方案名称"));return;}
            task("正在保存方案…",()->{
                JSONObject body=new JSONObject().put("name",title);
                if(copy){String raw=template==null?(kind.equals("dns")?"dns {\n upstream { resolver: 'udp://223.5.5.5:53' }\n routing { request { fallback: resolver } }\n}\n":"fallback: direct\n"):template.getString(sectionKey(kind));validateSection(kind,raw);body.put(sectionKey(kind),raw);return api.post(kind,body);}
                ensureSectionVersion(kind,template);return api.patch(kind+"/"+template.getLong("id"),body);
            },j->{if(copy)sectionDetails(kind,j.getLong("id"));else showPage(sectionPage(kind));});
        });
    }
    private void ensureSectionVersion(String kind,JSONObject section)throws Exception{
        JSONObject latest=api.get(kind+"/"+section.getLong("id"));if(latest.optLong("version")!=section.optLong("version"))throw new Exception("方案已被其他页面修改，请取消并刷新后重试");
    }
    private void validateSection(String kind,String raw)throws Exception{
        String full=api.get("user/me/dae-config-file").getString("content"),key=sectionKey(kind);
        ResourceText.Block block=ResourceText.block(full,key,0,full.length());String wrapped=ResourceText.wrap(key,raw);
        String candidate=block==null?full+"\n"+wrapped:full.substring(0,block.start)+wrapped+full.substring(block.close+1);
        api.post("user/me/dae-config-file/preview",new JSONObject().put("content",candidate));
    }
    private void saveSection(String kind,JSONObject section,String raw){
        task("正在校验并保存…",()->{validateSection(kind,raw);ensureSectionVersion(kind,section);return api.patch(kind+"/"+section.getLong("id"),new JSONObject().put(sectionKey(kind),raw));},j->{sectionDetails(kind,section.optLong("id"));Toast.makeText(this,"已保存，应用配置后生效",Toast.LENGTH_LONG).show();});
    }
    private void sectionDetails(String kind,long id){
        task("正在读取方案…",()->new JSONObject().put("section",api.get(kind+"/"+id)).put("groups",api.get("groups?summary=true")),result->{
            JSONObject section=result.getJSONObject("section");resourceGroups=items(result.getJSONObject("groups"));
            content.removeAllViews();actions.clear();generation++;detailSection=section;detailKind=kind;pageTitle.setText(section.optString("name"));
            LinearLayout top=card(kind.equals("dns")?"DNS 解析":"流量路由");hint(top,section.optBoolean("selected")?"当前已选方案 · 修改后需应用配置":"备用方案 · 可先编辑再选择");
            paired(top,"返回列表",()->showPage(sectionPage(kind)),"完整文本",()->sectionSourceForm(kind,section));
            try{if(kind.equals("dns"))dnsCards(section);else ruleCards(kind,section,"");}
            catch(Exception e){hint(card("保留自定义内容"),"此方案包含复杂结构，可使用完整文本编辑；原文已保留。");}
            scroll.post(()->scroll.scrollTo(0,0));
        });
    }
    private void sectionSourceForm(String kind,JSONObject section){
        LinearLayout fields=formFields();hint(fields,"高级语法可在这里编辑，保存前会检查完整配置。");EditText source=field(fields,"完整方案",section.optString(sectionKey(kind)),true,false);source.setTypeface(Typeface.MONOSPACE);
        form("完整文本",fields,()->saveSection(kind,section,source.getText().toString()));
    }
    private String[] upstream(ResourceText.Entry entry){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?s)^([\\p{L}_][\\p{L}\\p{N}_-]*)\\s*:\\s*(['\"])(.*)\\2$").matcher(entry.text);
        return m.matches()?new String[]{m.group(1),m.group(3).replace("\\'","'").replace("\\\\","\\")}:null;
    }
    private List<String> dnsAliases(String raw){
        ArrayList<String> aliases=new ArrayList<>();ResourceText.Block b=ResourceText.path(raw,"dns","upstream");
        if(b!=null)for(ResourceText.Entry entry:ResourceText.entries(raw,b.open+1,b.close)){String[] value=upstream(entry);if(value!=null)aliases.add(value[0]);}return aliases;
    }
    private String endpointLabel(String value){
        try{java.net.URI uri=new java.net.URI(value);if(uri.getRawQuery()!=null||uri.getRawUserInfo()!=null)return uri.getScheme()+"://"+(uri.getHost()==null?"上游地址":uri.getHost())+(uri.getPort()<0?"":":"+uri.getPort())+(uri.getPath()==null?"":uri.getPath());}catch(Exception ignored){}return value;
    }
    private void dnsCards(JSONObject section){
        String raw=ResourceText.wrap("dns",section.optString("dns"));LinearLayout heading=card("解析上游");hint(heading,"每个上游拥有独立名称，供查询规则选择。");button(heading,"添加上游",false,()->upstreamForm(section,null));
        ResourceText.Block block=ResourceText.path(raw,"dns","upstream");
        if(block!=null)for(ResourceText.Entry entry:ResourceText.entries(raw,block.open+1,block.close)){
            String[] values=upstream(entry);if(values==null){hint(card("自定义上游"),entry.text);continue;}
            LinearLayout c=card(values[0]);hint(c,endpointLabel(values[1]));
            paired(c,"编辑地址",()->upstreamForm(section,entry),"移除",()->{
                for(String phase:new String[]{"request","response"})for(ResourceText.Entry rule:ResourceText.rules(raw,true,phase)){
                    String destination=rule.fallback()?rule.text.substring(rule.text.indexOf(':')+1).trim():rule.arrow()<0?"":rule.text.substring(rule.arrow()+2).trim();
                    if(destination.equals(values[0])||destination.startsWith(values[0]+"(")){error(new Exception("此上游仍被规则引用，请先修改对应规则"));return;}
                }
                confirm("移除上游","将移除“"+values[0]+"”。",()->saveSection("dns",section,ResourceText.remove(raw,entry)));
            })[1].setTextColor(appearance.danger);
        }
        ruleCards("dns",section,"request");ruleCards("dns",section,"response");
    }
    private void upstreamForm(JSONObject section,ResourceText.Entry existing){
        String raw=ResourceText.wrap("dns",section.optString("dns"));String[] original=existing==null?null:upstream(existing);
        LinearLayout fields=formFields();EditText alias=field(fields,"上游名称",original==null?"":original[0],false,false);if(existing!=null)alias.setEnabled(false);
        EditText address=field(fields,"上游地址",original==null?"":original[1],false,false);hint(fields,"例如 udp://223.5.5.5:53 或 https://dns.google/dns-query。已有名称保持固定，避免影响引用。");
        form(existing==null?"添加上游":"编辑上游",fields,()->{
            try{String name=alias.getText().toString().trim(),url=address.getText().toString().trim();if(!name.matches("[A-Za-z_][A-Za-z0-9_-]*"))throw new Exception("名称请使用字母、数字、下划线或连字符");if(url.isEmpty()||url.indexOf('\n')>=0)throw new Exception("请填写单行上游地址");
                String line=name+": "+ResourceText.quoted(url),edited;
                if(existing!=null)edited=ResourceText.replace(raw,existing,line);
                else{if(dnsAliases(raw).contains(name))throw new Exception("上游名称已存在");String prepared=ResourceText.ensureDnsPath(raw,"upstream");ResourceText.Block b=ResourceText.path(prepared,"dns","upstream");edited=prepared.substring(0,b.close)+"\n  "+line+"\n"+prepared.substring(b.close);}
                saveSection("dns",section,edited);
            }catch(Exception e){error(e);}
        });
    }
    private String[] ruleDestinations(String kind,String phase,String raw){
        LinkedHashSet<String> values=new LinkedHashSet<>();if(kind.equals("dns")){values.addAll(dnsAliases(raw));if(phase.equals("response")){values.add("accept");values.add("reject");}else values.add("asis");}
        else{values.add("direct");values.add("block");for(int i=0;i<resourceGroups.length();i++)values.add(resourceGroups.optJSONObject(i).optString("name"));}
        return values.toArray(new String[0]);
    }
    private void ruleCards(String kind,JSONObject section,String phase){
        boolean dns=kind.equals("dns");String raw=dns?ResourceText.wrap("dns",section.optString("dns")):section.optString("routing");
        List<ResourceText.Entry> all=ResourceText.rules(raw,dns,phase);ArrayList<ResourceText.Entry> rules=new ArrayList<>();ResourceText.Entry fallback=null;
        for(ResourceText.Entry entry:all){if(entry.fallback())fallback=entry;else rules.add(entry);}
        LinearLayout head=card(dns?(phase.equals("request")?"查询规则":"响应规则"):"匹配规则");hint(head,dns?(phase.equals("request")?"按查询条件选择解析上游。":"对返回结果接受、拒绝或重新选择上游。"):"自上而下匹配，靠前的规则优先。");button(head,"添加规则",false,()->ruleForm(kind,section,phase,null,false));
        for(int i=0;i<rules.size();i++){
            final int index=i;ResourceText.Entry entry=rules.get(i);int arrow=entry.arrow();LinearLayout c=card(String.format(Locale.ROOT,"规则 %02d",i+1));
            if(arrow<0){hint(c,entry.text);hint(c,"自定义结构，完整文本中可编辑");continue;}
            TextView condition=text(entry.text.substring(0,arrow).trim(),13,ink);condition.setTypeface(Typeface.MONOSPACE);c.addView(condition);c.addView(text("→  "+entry.text.substring(arrow+2).trim(),17,accent));
            paired(c,"编辑规则",()->ruleForm(kind,section,phase,entry,false),"更多",()->{
                ArrayList<String> options=new ArrayList<>();if(index>0)options.add("上移");if(index+1<rules.size())options.add("下移");options.add("删除规则");
                new AlertDialog.Builder(this).setTitle("规则 "+(index+1)).setItems(options.toArray(new String[0]),(d,choice)->{
                    String action=options.get(choice);if(action.equals("删除规则"))confirm("删除规则","将删除这条匹配规则。",()->saveSection(kind,section,ResourceText.remove(raw,entry)));
                    else saveSection(kind,section,ResourceText.swap(raw,entry,rules.get(index+(action.equals("上移")?-1:1))));
                }).show();
            });
        }
        ResourceText.Entry last=fallback;LinearLayout end=card("兜底行为");hint(end,last==null?"尚未显式设置":last.text.substring(last.text.indexOf(':')+1).trim());
        button(end,"设置兜底",false,()->ruleForm(kind,section,phase,last,true));
    }
    private void ruleForm(String kind,JSONObject section,String phase,ResourceText.Entry existing,boolean fallback){
        boolean dns=kind.equals("dns");String raw=dns?ResourceText.wrap("dns",section.optString("dns")):section.optString("routing");
        LinearLayout fields=formFields();String value=existing==null?"":existing.text;int arrow=existing==null?-1:existing.arrow();
        EditText condition=fallback?null:field(fields,"匹配条件",arrow<0?"":value.substring(0,arrow).trim(),true,false);
        if(condition!=null){String[] presets=dns?(phase.equals("request")?new String[]{"qname(geosite:cn)","qtype(aaaa)"}:new String[]{"ip(geoip:cn)","upstream(resolver)"}):new String[]{"domain(suffix:example.com)","dip(geoip:cn)","l4proto(udp) && dport(443)"};
            choicesTemplate(fields,condition,presets);hint(fields,"可组合多个条件，保存前会按完整配置校验。");}
        String current=fallback?(existing==null?(dns?(phase.equals("response")?"accept":"asis"):"direct"):value.substring(value.indexOf(':')+1).trim()):(arrow<0?(dns?(phase.equals("response")?"accept":"asis"):"direct"):value.substring(arrow+2).trim());
        Spinner destination=choices(fields,dns?"解析目标 / 行为":"流量出口",current,ruleDestinations(kind,phase,raw));
        form(fallback?"设置兜底":existing==null?"添加规则":"编辑规则",fields,()->{
            try{String target=destination.getSelectedItem().toString();String expr=condition==null?"":condition.getText().toString().trim();if(!fallback&&(expr.isEmpty()||ResourceText.operator(expr,"->")>=0))throw new Exception("请填写匹配条件，出口通过下方选项选择");
                String line=fallback?"fallback: "+target:expr+" -> "+target;
                String edited=existing==null?ResourceText.insertRule(raw,dns,phase,line):ResourceText.replace(raw,existing,line);saveSection(kind,section,edited);
            }catch(Exception e){error(e);}
        });
    }
    private void choicesTemplate(LinearLayout parent,EditText condition,String[] presets){
        button(parent,"使用条件模板",false,()->new AlertDialog.Builder(this).setTitle("选择条件模板").setItems(presets,(d,index)->condition.setText(presets[index])).show());
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
