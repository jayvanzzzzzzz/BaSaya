import test from "node:test";
import assert from "node:assert/strict";
import { score } from "../speech-api-fix/lib/pronunciationScoring.js";

test("opinyon accepts the recognizer spelling in words and split transcripts", () => {
  for (const heard of ["opinyon", "Opinion!", "ang opinion", "o pin yon", "opi nion"]) {
    assert.equal(score("opinyon", [heard]).band, "great", heard);
  }
  assert.equal(score("opinion", ["opinyon"]).band, "great");
});

test("opinyon rejects different words and endings", () => {
  for (const heard of ["opinyong", "opisyon", "opinyan", "pinyon", "onion"]) {
    assert.equal(score("opinyon", [heard]).band, "try-again", heard);
  }
});

test("existing scoring boundaries remain intact", () => {
  for (const [target, heard, band] of [
    ["bahay", "ang bahay", "great"],
    ["bahay", "ba hay", "great"],
    ["puno", "punu", "great"],
    ["babae", "babai", "great"],
    ["mataba", "matabak", "try-again"],
    ["bata", "bataa", "try-again"],
    ["paaralan", "paaralang", "okay"],
    ["paaralan", "paralang", "try-again"],
    ["bata", "pusa", "try-again"],
  ]) assert.equal(score(target, [heard]).band, band, `${target}: ${heard}`);
  assert.equal(score("aso", ["ako", "aso"]).band, "great");
  assert.equal(score("aso", []).band, "try-again");
});

test("glide spelling rules apply across words in both directions", () => {
  for (const [target, heard] of [
    ["opinyon", "opiniyon"], ["leksyon", "leksiyon"],
    ["leksyon", "leksion"], ["aksyon", "aksiyon"],
    ["pasyon", "pasion"], ["misyon", "mision"],
    ["dyaryo", "diyaryo"], ["tyempo", "tiempo"],
    ["kwento", "kuwento"], ["kwarto", "kuarto"],
    ["biskwit", "biskuwit"], ["indibidwal", "indibidual"],
  ]) {
    assert.equal(score(target, [heard]).band, "great", `${target} / ${heard}`);
    assert.equal(score(heard, [target]).band, "great", `${heard} / ${target}`);
  }
});

test("multiple contiguous fragments inside phrases are joined", () => {
  for (const [target, heard] of [
    ["opinyon", "ang o pin yon po"],
    ["leksyon", "ang lek si yon po"],
    ["kwento", "ang ku wen to po"],
  ]) assert.equal(score(target, [heard]).band, "great", `${target} / ${heard}`);
  assert.equal(score("opinyon", ["o ibang pin yon"]).band, "try-again");
});

test("phonetic tolerance preserves consonants and vowel hiatus", () => {
  for (const [target, heard] of [
    ["misyon", "nasyon"], ["pasyon", "pasyonko"],
    ["kwento", "kwenta"], ["bata", "pata"],
    ["leon", "leyon"], ["buan", "ban"],
  ]) assert.equal(score(target, [heard]).band, "try-again", `${target} / ${heard}`);
});
