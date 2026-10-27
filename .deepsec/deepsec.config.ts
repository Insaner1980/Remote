import { defineConfig, regexMatcher } from "deepsec/config";

export default defineConfig({
  plugins: [{
    name: "remote-certificate-trust",
    matchers: [{
      slug: "remote-certificate-trust",
      description: "Review the TV certificate trust boundary, including first-use and pinned certificates.",
      noiseTier: "normal",
      filePatterns: ["app/src/main/java/com/emmah/colordock/remote/tv/TvCertificateTrust.kt"],
      examples: ["override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {"],
      match(content) {
        return regexMatcher("remote-certificate-trust", [
          { regex: /\bfun\s+checkServerTrusted\s*\(/g, label: "TV certificate trust validation" }
        ], content);
      }
    }]
  }],
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
