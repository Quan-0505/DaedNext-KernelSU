import { exec } from './kernelsu.js';
const $ = (id) => document.getElementById(id);
const ctl='/data/adb/modules/daenext/bin/daenextctl';
let busy=false;
const allowed=new Set(['start','stop','restart','reload','backup','logs','status','autostart on','autostart off','google-network on','google-network off']);
async function command(action){
 if(!allowed.has(action)) throw new Error('Unsupported action');
 const result=await exec(`${ctl} ${action}`);
 if(result.errno!==0) throw new Error(result.stderr||result.stdout||`操作失败 (${result.errno})`);
 return result.stdout.trim();
}
function message(text){$('message').hidden=false;$('message').textContent=text;}
async function refresh(){
 try{
  const s=JSON.parse(await command('status'));
  const active=s.readiness?.runtimeRunning===true;
  const running=s.state==='running'||s.state==='degraded';
  $('state').textContent=running?(active?'内核运行中':s.state==='degraded'?'内核需要检查':'服务已就绪'):({starting:'正在启动',error:'需要检查',stopped:'服务已停止'}[s.state]||s.state);
  $('indicator').classList.toggle('on',running);
  $('detail').textContent=running?(active?'透明代理已启动，使用管理面板查看连接。':s.state==='degraded'?'可打开管理面板调整配置，或查看服务日志。':'打开管理面板配置节点并启动内核。'):(s.state==='error'?'打开日志查看具体原因。':'启动服务后即可进入管理面板。');
  $('network').textContent=s.interface||'暂未连接';
  $('pid').textContent=s.pid||'—';
  $('autostart').checked=s.autostart;
  $('google-network').checked=s.googleNetworkCompat===true;
 }catch(e){$('state').textContent='无法读取服务';$('detail').textContent=typeof window.ksu==='undefined'?'请在 KernelSU 的模块 WebUI 中打开本页。':e.message;}
}
async function perform(action){
 if(busy)return;
 busy=true;document.querySelectorAll('button,input').forEach(x=>x.disabled=true);
 try{
  const output=await command(action);
  if(action==='logs'){$('log-section').hidden=false;$('log-output').textContent=output||'暂无日志';}
  else message(action==='backup'?`备份已保存：\n${output}`:({'start':'服务已启动','stop':'服务已停止','restart':'服务已重启','reload':'配置已重载'}[action]||'设置已保存'));
 }catch(e){message(e.message);}
 finally{busy=false;document.querySelectorAll('button,input').forEach(x=>x.disabled=false);await refresh();}
}
for(const action of ['start','stop','restart','reload','backup','logs']) $(action).onclick=()=>perform(action);
$('autostart').onchange=()=>perform(`autostart ${$('autostart').checked?'on':'off'}`);
$('google-network').onchange=()=>perform(`google-network ${$('google-network').checked?'on':'off'}`);
$('panel').onclick=(event)=>{if($('state').textContent==='服务已停止'){event.preventDefault();message('请先启动服务，再进入管理面板。');}};
refresh();setInterval(()=>{if(!busy)refresh();},6000);
