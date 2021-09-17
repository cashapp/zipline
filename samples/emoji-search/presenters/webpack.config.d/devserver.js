if (config.devServer) {
//  config.devServer.disableHostCheck = true; // Accept calls by IP as well as for 'localhost'
  config.devServer.host = "0.0.0.0"; // Accept calls from devices on the same WiFi network.
  config.devServer.liveReload = false; // Don't inject live reload JavaScript; it upsets Duktape.
  config.devServer.hot = false; // Don't inject live reload JavaScript; it upsets Duktape.
  config.devServer.client = false;
  delete config.devServer.open; // Don't open Chrome.
}
