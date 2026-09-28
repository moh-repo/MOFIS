// Karma configuration file, see https://karma-runner.github.io/1.0/config/configuration-file.html

module.exports = function (config) {
  config.set({
    basePath: '',
    // The test target runs in the application (esbuild) builder mode, which
    // bundles the specs and initialises TestBed itself — the old
    // '@angular-devkit/build-angular' karma framework/plugin is not used.
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage')
    ],
    client: {
      jasmine: {},
      clearContext: false
    },
    jasmineHtmlReporter: {
      suppressAll: true
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/mof-is'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'text-summary' }
      ]
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    // Headless Chrome defaults to an 800x600 window, which is below the 992px
    // breakpoint — the desktop layout the sidebar/content spec measures never
    // applies there. Give it a desktop-sized viewport.
    customLaunchers: {
      ChromeHeadlessWide: {
        base: 'ChromeHeadless',
        flags: ['--window-size=1600,900']
      }
    },
    browsers: ['Chrome', 'ChromeHeadless'],
    // The application builder serves the whole spec bundle at once; on a busy
    // machine the headless browser can miss karma's 5 s socket ping while it
    // parses it and gets dropped as "Disconnected (ping timeout)" before a
    // single test runs. Give it room.
    pingTimeout: 30000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 2,
    browserNoActivityTimeout: 120000,
    singleRun: false,
    restartOnFileChange: true
  });
};
