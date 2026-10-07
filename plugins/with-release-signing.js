// Signs the release build with a fixed upload key when the build is given one, so
// every APK installs over the previous one. Without a key, release falls back to the
// debug key so the APK still installs.
//
// The key comes from Gradle properties (CI passes them with -P):
//   vescStoreFile, vescStorePassword, vescKeyAlias, vescKeyPassword
const { withAppBuildGradle } = require('expo/config-plugins');

const RELEASE_CONFIG = `
        release {
            if (findProperty('vescStoreFile')) {
                storeFile file(findProperty('vescStoreFile'))
                storePassword findProperty('vescStorePassword')
                keyAlias findProperty('vescKeyAlias')
                keyPassword findProperty('vescKeyPassword')
            }
        }`;

function withReleaseSigning(config) {
  return withAppBuildGradle(config, (cfg) => {
    let gradle = cfg.modResults.contents;
    if (gradle.includes("findProperty('vescStoreFile')")) return cfg;
    const signingBlock = /signingConfigs\s*\{/;
    if (!signingBlock.test(gradle)) throw new Error('release signing: no signingConfigs block');
    gradle = gradle.replace(signingBlock, (m) => `${m}${RELEASE_CONFIG}`);
    const releaseType = /(buildTypes\s*\{[\s\S]*?release\s*\{[\s\S]*?)signingConfig\s+signingConfigs\.debug/;
    if (!releaseType.test(gradle)) throw new Error('release signing: no release signingConfig line');
    gradle = gradle.replace(
      releaseType,
      `$1signingConfig findProperty('vescStoreFile') ? signingConfigs.release : signingConfigs.debug`,
    );
    cfg.modResults.contents = gradle;
    return cfg;
  });
}

module.exports = withReleaseSigning;
