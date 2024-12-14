### usage
```javascript
const plg = $plugin.load('cn.zzliux.assttyys.plugin');
const img = $images.read('/file/to/path');
const result = plg.ocr.detect(img);
console.log(result);
```



### 开发环境
1. idea + android插件 OR android studio
2. 配置android sdk >= 33
3. 配置java jdk >= 21
4. idea或as会自动根据gradle/wrapper/gradle-wrapper.properties下载当前配置的gradle
5. 开发运行不需要签名，如需自行打包需修改签名配置app/build.gradle的signingConfigs
6. 编译：./gradlew assemblyRelease



