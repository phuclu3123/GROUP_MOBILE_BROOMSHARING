'use strict';
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'), folder=path.join(root,'build/acceptance');
const state=JSON.parse(fs.readFileSync(path.join(folder,'run.json')));
if(state.cleaned)throw new Error('Fixtures already cleaned');
state.androidAttempts=(state.androidAttempts||0)+1;
fs.writeFileSync(path.join(folder,'run.json'),JSON.stringify(state,null,2));
const adb=path.join(process.env.LOCALAPPDATA,'Android/Sdk/platform-tools/adb.exe');
function run(args,log) {
  const r=spawnSync(adb,args,{encoding:'utf8',timeout:300000,windowsHide:true});
  fs.writeFileSync(path.join(folder,log),(r.stdout||'')+(r.stderr||''));
  if(r.error||r.status!==0)throw new Error('ADB operation failed: '+log);
  return r.stdout||'';
}
run(['install','-r',path.join(root,'app/build/outputs/apk/debug/app-debug.apk')],'install-app.log');
run(['install','-r',path.join(root,'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')],'install-test.log');
const args=['shell','am','instrument','-w','-r','-e','class','vn.edu.ueh.phuclu.broomsharing.LiveBookingAcceptanceTest,vn.edu.ueh.phuclu.broomsharing.LiveGuestUiAcceptanceTest','-e','runLiveBooking','true','-e','roomId',state.roomId,'-e','startAt',String(state.start+state.androidAttempts*7200000),'-e','runId',state.run+'_'+Date.now()];
for(const label of ['guest','host','admin'])args.push('-e',label+'Email',state.accounts[label].email,'-e',label+'Password',state.accounts[label].password);
args.push('vn.edu.ueh.phuclu.broomsharing.test/androidx.test.runner.AndroidJUnitRunner');
const result=run(args,'android-instrumentation.log');
if(!/OK \(2 tests\)/.test(result))throw new Error('Android acceptance failed; see sanitized instrumentation log');
for(const name of ['guest','host','guest-form','guest-confirmation','guest-favorites'])run(['pull','/sdcard/Android/data/vn.edu.ueh.phuclu.broomsharing/files/acceptance-'+name+'.png',path.join(folder,'android-'+name+'.png')],'pull-'+name+'.log');
console.log('PASS Android real Auth, local Functions, real named Firestore; Guest/Host lists and Admin actions');
