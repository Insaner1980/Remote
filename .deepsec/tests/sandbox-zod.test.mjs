import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
const deepsecRequire = createRequire(require.resolve("deepsec"));
const sandboxRoot = dirname(deepsecRequire.resolve("@vercel/sandbox/package.json"));

for (const extension of ["js", "cjs"]) {
  const { NetworkPolicyValidator } = await import(
    pathToFileURL(join(sandboxRoot, "dist", "api-client", `validators.${extension}`))
  );

  test(`Sandbox ${extension} accepts string headers with Zod 4`, () => {
    const policy = {
      mode: "custom",
      injectionRules: [{ domain: "example.com", headers: { "X-Test": "value" } }],
    };
    assert.deepEqual(NetworkPolicyValidator.parse(policy), policy);
  });

  test(`Sandbox ${extension} rejects non-string headers without crashing`, () => {
    const result = NetworkPolicyValidator.safeParse({
      mode: "custom",
      injectionRules: [{ domain: "example.com", headers: { "X-Test": 123 } }],
    });
    assert.equal(result.success, false);
    assert.ok(result.error.issues.length > 0);
  });
}
