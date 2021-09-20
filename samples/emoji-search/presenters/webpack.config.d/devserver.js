if (config.devServer) {
  // Accept calls from any device. Typically this is a phone on the same WiFi network.
  config.devServer.host = "0.0.0.0";

  // Don't inject served JavaScript with live reloading features.
  config.devServer.liveReload = false;
  config.devServer.hot = false;
  config.devServer.client = false;

  // Don't open Chrome.
  delete config.devServer.open;
}
