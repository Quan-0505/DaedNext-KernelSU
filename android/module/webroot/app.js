import { exec } from './kernelsu.js';
const $ = (id) => document.getElementById(id);
const ctl='/data/adb/modules/daenext/bin/daenextctl';
let busy=false;
let wifiLoaded=false;
let wifiConfig={enabled:false,mode:'blacklist',blacklist:[],whitelist:[],proxy_on_non_wifi:true};
let displayedMode='blacklist';
const allowed=new Set(['start','stop','restart','reload','backup','logs','status','autostart on','autostart off','google-network on','google-network off','wifi-policy get']);
async function command(action){
 if(!allowed.has(action)&&!(action.length<24000&&/^wifi-policy set [A-Za-z0-9+/=]+$/.test(action))) throw new Error('Unsupported action');
 const result=await exec(`${ctl} ${action}`);
 if(result.errno!==0) throw new Error(result.stderr||result.stdout||`操作失败 (${result.errno})`);
 return result.stdout.trim();
}
function message(text){$('message').hidden=false;$('message').textContent=text;}
function names(){return $('wifi-list').value.split(/\r?\n/).filter(x=>x.length>0);}
function showList(mode){
 displayedMode=mode;
 $('wifi-mode').value=mode;
 $('wifi-list-label').textContent=mode==='blacklist'?'Wi-Fi 黑名单':'Wi-Fi 白名单';
 $('wifi-list').value=(wifiConfig[mode]||[]).join('\n');
}
async function loadWifi(){
 const info=JSON.parse(await command('wifi-policy get'));
 wifiConfig=info.config;
 $('wifi-enabled').checked=wifiConfig.enabled;
 $('wifi-cellular').checked=wifiConfig.proxy_on_non_wifi;
 showList(wifiConfig.mode);
 wifiLoaded=true;
}
async function refresh(){
 try{
  const s=JSON.parse(await command('status'));
  const active=s.readiness?.runtimeRunning===true;
  const running=s.state==='running'||s.state==='degraded';
  const bypass=running&&s.readiness?.networkPolicyPaused===true&&s.readiness?.runtimeRequired===true;
  $('state').textContent=running?(bypass?'Wi-Fi 策略直连':active?'内核运行中':s.state==='degraded'?'内核需要检查':'服务已就绪'):({starting:'正在启动',error:'需要检查',stopped:'服务已停止'}[s.state]||s.state);
  $('indicator').classList.toggle('on',running);
  $('detail').textContent=running?(bypass?'手机代理已旁路，流量交由当前网络；网络策略变化后自动恢复。':active?'透明代理已启动，使用管理面板查看连接。':s.state==='degraded'?'可打开管理面板调整配置，或查看服务日志。':'打开管理面板配置节点并启动内核。'):(s.state==='error'?'打开日志查看具体原因。':'启动服务后即可进入管理面板。');
  $('network').textContent=s.interface||'暂未连接';
  $('pid').textContent=s.pid||'—';
  $('autostart').checked=s.autostart;
  $('google-network').checked=s.googleNetworkCompat===true;
  const p=s.wifiPolicy;
  $('wifi-current').textContent=p?.enabled?`当前：${p.ssid||(p.network==='wifi'?'Wi-Fi 名称暂不可用':p.network==='unavailable'?'等待网络':'非 Wi-Fi 网络')} · 策略：${p.direct?'直连':'代理'}${!active&&!bypass?' · 内核尚未启动':''}`:'Wi-Fi 自动切换未启用，使用原有代理规则。';
 }catch(e){$('state').textContent='无法读取服务';$('detail').textContent=typeof window.ksu==='undefined'?'请在 KernelSU 的模块 WebUI 中打开本页。':e.message;}
}
function disableControls(disabled){document.querySelectorAll('button,input,select,textarea').forEach(x=>x.disabled=disabled);}
async function perform(action){
 if(busy)return;
 busy=true;disableControls(true);
 try{
  const output=await command(action);
  if(action==='logs'){$('log-section').hidden=false;$('log-output').textContent=output||'暂无日志';}
  else message(action==='backup'?`备份已保存：\n${output}`:({'start':'服务已启动','stop':'服务已停止','restart':'服务已重启','reload':'配置已重载'}[action]||'设置已保存，网络策略将在数秒内生效。'));
  if(action.startsWith('wifi-policy set '))await loadWifi();
 }catch(e){message(e.message);}
 finally{busy=false;disableControls(false);await refresh();}
}
for(const action of ['start','stop','restart','reload','backup','logs']) $(action).onclick=()=>perform(action);
$('autostart').onchange=()=>perform(`autostart ${$('autostart').checked?'on':'off'}`);
$('google-network').onchange=()=>perform(`google-network ${$('google-network').checked?'on':'off'}`);
$('wifi-mode').onchange=()=>{wifiConfig[displayedMode]=names();showList($('wifi-mode').value);};
$('wifi-save').onclick=()=>{
 if(!wifiLoaded){message('Wi-Fi 设置尚未读取完成，请稍后重试。');return;}
 wifiConfig[displayedMode]=names();
 wifiConfig={...wifiConfig,enabled:$('wifi-enabled').checked,mode:$('wifi-mode').value,proxy_on_non_wifi:$('wifi-cellular').checked};
 const bytes=new TextEncoder().encode(JSON.stringify(wifiConfig));
 const data=btoa(Array.from(bytes,b=>String.fromCharCode(b)).join(''));
 perform(`wifi-policy set ${data}`);
};
$('wifi-add').onclick=async()=>{
 if(busy)return;
 busy=true;disableControls(true);
 try{
  const info=JSON.parse(await command('wifi-policy get'));
  const ssid=info.current?.ssid;
  if(!ssid)throw new Error('当前未连接可识别的 Wi-Fi，请连接后重试。');
  const list=names();if(!list.includes(ssid))list.push(ssid);
  $('wifi-list').value=list.join('\n');
  message('已添加当前 Wi-Fi，点击“保存 Wi-Fi 设置”后生效。');
 }catch(e){message(e.message);}
 finally{busy=false;disableControls(false);}
};
$('panel').onclick=(event)=>{if($('state').textContent==='服务已停止'){event.preventDefault();message('请先启动服务，再进入管理面板。');}};
refresh();loadWifi().catch(e=>message(e.message));setInterval(()=>{if(!busy)refresh();},6000);
