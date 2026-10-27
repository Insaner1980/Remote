import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import config from "../deepsec.config.ts";

test("TV certificate trust is a review candidate, not a vulnerability assertion", () => {
  const matcher = config.plugins.flatMap((plugin) => plugin.matchers)
    .find((candidate) => candidate.slug === "remote-certificate-trust");
  const path = "app/src/main/java/com/emmah/colordock/remote/tv/TvCertificateTrust.kt";
  const content = readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");
  assert.deepEqual(matcher.filePatterns, [path]);
  assert.equal(matcher.noiseTier, "normal");
  assert.ok(matcher.match(content, path).length > 0);
  for (const example of matcher.examples) {
    assert.ok(matcher.match(example, path).length > 0);
  }
  assert.equal(matcher.match("class Unrelated {}", path).length, 0);
});
