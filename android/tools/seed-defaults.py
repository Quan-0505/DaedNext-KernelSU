import re,sqlite3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
out=root/'module/defaults'
out.mkdir(parents=True,exist_ok=True)
path=out/'daed.db'
if path.exists():
 old=sqlite3.connect(f'file:{path}?mode=ro',uri=True)
 for table in ('users','nodes','subscriptions'):
  if old.execute('select count(*) from '+table).fetchone()[0]:
   raise SystemExit('Refusing to overwrite a non-empty default database')
 old.close()
 path.unlink()
db=sqlite3.connect(path)
source=(root.parent/'crates/dae-product-persistence/src/state/schema.rs').read_text(encoding='utf8')
schema=re.search(r'r#"(.*?)"#',source,re.S).group(1)
db.executescript(schema)
global_conf='''global {
 tproxy_port: 12345
 wan_interface: auto
 log_level: info
 so_mark_from_dae: 16777216
 tproxy_port_protect: true
 dial_mode: "domain+"
 tcp_check_url: "http://cp.cloudflare.com,1.1.1.1"
 udp_check_dns: "223.5.5.5:53"
 check_interval: "50s"
}
'''
dns='''dns {
 upstream {
  alidns: "udp://223.5.5.5:53"
 }
 routing {
  request { fallback: alidns }
 }
}
'''
routing='''routing {
 dip(geoip:private) -> direct
 fallback: direct
}
'''
db.execute('insert into configs(id,name,"global",selected,version) values(1,?,?,1,1)',('global',global_conf))
db.execute('insert into dns(id,name,dns,selected,version) values(1,?,?,1,1)',('default',dns))
db.execute('insert into routings(id,name,routing,selected,version) values(1,?,?,1,1)',('default',routing))
db.execute("insert into groups(id,name,policy,version,system_id) values(1,'proxy','fixed(0)',1,1)")
db.execute('insert into systems(id,running,running_config_id,running_dns_id,running_routing_id) values(1,0,1,1,1)')
db.execute("insert into daed_product_metadata(key,value) values('runtime_log_level','info')")
db.execute("insert into daed_product_metadata(key,value) values('runtime_external_input_version','0')")
db.commit()
assert db.execute('select count(*) from users').fetchone()[0]==0
assert db.execute('select count(*) from nodes').fetchone()[0]==0
assert db.execute('select count(*) from subscriptions').fetchone()[0]==0
db.close()
(out/'config.dae').write_text(global_conf+'\n'+dns+'\n'+routing,encoding='utf8',newline='\n')
print('Created empty Android defaults: no accounts, nodes or subscriptions.')
