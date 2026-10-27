import { defineConfig } from "deepsec/config";

export default defineConfig({
  projects: [
    {
      id: "remote",
      root: "..",
      promptAppend:
        "Prioritize TV discovery and endpoint validation, TLS certificate trust and pairing credential storage, companion uploads and image handling, exported Android components, backup exclusions, and sensitive logging. Account for the documented local SSDP HTTP compatibility exception when assessing cleartext traffic. Do not connect to, pair with, or modify physical TVs or other devices.",
      priorityPaths: [
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/xml/",
        "app/src/main/java/com/emmah/colordock/remote/tv/",
        "app/src/main/java/com/emmah/colordock/remote/companion/",
        "app/src/main/java/com/emmah/colordock/remote/images/"
      ]
    }
  ]
});
