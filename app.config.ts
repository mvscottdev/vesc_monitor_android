import type { ExpoConfig } from 'expo/config';

const config: ExpoConfig = {
  name: 'VESC Monitor',
  slug: 'vesc-monitor',
  version: '0.1.0',
  orientation: 'landscape',
  icon: './assets/images/icon.png',
  scheme: 'vescmonitor',
  userInterfaceStyle: 'dark',
  backgroundColor: '#0b0f14',
  android: {
    package: 'dev.vescmonitor.app',
    versionCode: 1,
    adaptiveIcon: {
      backgroundColor: '#0b0f14',
      foregroundImage: './assets/images/android-icon-foreground.png',
      backgroundImage: './assets/images/android-icon-background.png',
      monochromeImage: './assets/images/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
    // Ride history is not backed up (no backup or export of rides).
    allowBackup: false,
  },
  plugins: [
    'expo-router',
    [
      'expo-build-properties',
      {
        android: {
          kotlinVersion: '2.1.20',
          minSdkVersion: 31,
          buildArchs: ['arm64-v8a'],
        },
      },
    ],
    './modules/vesc/app.plugin.js',
    './plugins/with-release-signing.js',
  ],
  experiments: {
    typedRoutes: true,
  },
};

export default config;
