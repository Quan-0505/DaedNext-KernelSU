import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const html=fs.readFileSync(new URL('../module/webroot/index.html',import.meta.url),'utf8');
const elements=new Map([...html.matchAll(/\bid="([^"]+)"/g)].map(([,id])=>[id,{value:'',checked:false,disabled:false,hidden:false,textContent:'',classList:{toggle(){}}}]));
let config={enabled:true,mode:'blacklist',blacklist:['Managed WiFi'],whitelist:['Ordinary WiFi'],proxy_on_non_wifi:true};
const commands=[];
const status={state:'running',pid:7,interface:'wlan0',autostart:true,googleNetworkCompat:true,
 readiness:{runtimeRunning:false,runtimeRequired:true,networkPolicyPaused:true},
 wifiPolicy:{enabled:true,direct:true,ssid:'Managed WiFi',network:'wifi'}};
const context={document:{getElementById:id=>{assert(elements.has(id),id);return elements.get(id);},querySelectorAll:()=>[...elements.values()]},TextEncoder,btoa,
 setInterval:()=>0,window:{ksu:{}},exec:async command=>{
  commands.push(command);const action=command.replace('/data/adb/modules/daenext/bin/daenextctl ','');
  if(action.startsWith('wifi-policy set '))config=JSON.parse(Buffer.from(action.slice(16),'base64').toString('utf8'));
  const result=action==='status'?status:action==='wifi-policy get'?{config,current:{ssid:'Current WiFi'}}:{ok:true};
  return {errno:0,stdout:JSON.stringify(result),stderr:''};
 }};
vm.createContext(context);
const js=fs.readFileSync(new URL('../module/webroot/app.js',import.meta.url),'utf8').replace("import { exec } from './kernelsu.js';",'const exec=globalThis.exec;');
vm.runInContext(js,context);
const settle=()=>new Promise(resolve=>setImmediate(resolve));await settle();
assert.equal(elements.get('state').textContent,'Wi-Fi 策略直连');
assert.equal(elements.get('wifi-list').value,'Managed WiFi');
elements.get('wifi-mode').value='whitelist';elements.get('wifi-mode').onchange();
assert.equal(elements.get('wifi-list').value,'Ordinary WiFi');
const ssid='测试 "$() WiFi';elements.get('wifi-list').value=ssid;
elements.get('wifi-save').onclick();await settle();
assert.deepEqual(config.blacklist,['Managed WiFi']);assert.deepEqual(config.whitelist,[ssid]);
assert.equal(config.mode,'whitelist');
assert.match(commands.find(c=>c.includes('wifi-policy set ')),/wifi-policy set [A-Za-z0-9+/=]+$/);
assert(!commands.some(c=>c.includes(ssid)),'SSID must remain encoded data');
elements.get('wifi-add').onclick();await settle();assert.equal(elements.get('wifi-list').value,ssid+'\nCurrent WiFi');
elements.get('wifi-mode').value='blacklist';elements.get('wifi-mode').onchange();assert.equal(elements.get('wifi-list').value,'Managed WiFi');
console.log('WebUI Wi-Fi mode, independent lists, Unicode transport and bypass display passed');
