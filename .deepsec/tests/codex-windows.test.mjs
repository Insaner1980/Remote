import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { test } from "node:test";
import { runInNewContext } from "node:vm";

const require = createRequire(import.meta.url);
const source = readFileSync(require.resolve("deepsec"), "utf8");
const start = source.indexOf("function buildCodexInvocation() {");
const end = source.indexOf("function cleanupCodexHome", start);
assert.ok(start >= 0 && end > start);
const invocation = source.slice(start, end);

function build(platform, env = {}) {
  return runInNewContext(`${invocation}\nbuildCodexInvocation()`, {
    process: { platform, env },
    findCodexSubscriptionAuth: () => "fixture-user-home",
    makeCodexHome: () => "fixture-temporary-home",
    mirrorCodexAuthJson: () => {},
    resolveCodexPaths: () => null,
    buildCodexEnv: (extras) => extras,
    buildGatewayProviderConfig: () => ({ name: "fixture-provider" }),
    CUSTOM_PROVIDER_ID: "fixture-provider",
  }).options;
}

test("Windows keeps MXC by default and allows an explicit supported sandbox", () => {
  for (const credentials of [{}, { OPENAI_API_KEY: "fixture-token" }]) {
    assert.equal(build("win32", credentials).config.windows.sandbox, "mxc");
    for (const sandbox of ["mxc", "elevated", "unelevated"]) {
      const options = build("win32", { ...credentials, DEEPSEC_WINDOWS_SANDBOX: sandbox });
      assert.equal(options.config.windows.sandbox, sandbox);
      assert.equal(options.config.features.plugins, false);
      assert.equal(options.config.features.remote_plugin, false);
    }
  }
});

test("Invalid Windows sandbox choices fail closed; other platforms are unchanged", () => {
  assert.throws(() => build("win32", { DEEPSEC_WINDOWS_SANDBOX: "disabled" }), /must be mxc, elevated, or unelevated/);
  assert.equal(build("linux", { DEEPSEC_WINDOWS_SANDBOX: "disabled" }).config.windows, undefined);
});
