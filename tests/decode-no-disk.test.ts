// decode_polaris_token.py's output step prints the token and writes no file.
// Run: bun test tests/decode-no-disk.test.ts
// Scope: scan_for_token (the btsnoop parser, which only reads the log) is
// stubbed; argument handling and the output step are real. Checked write
// locations: HOME, the log's folder, and every file in the repository
// (including .git), compared by content hash before and after.
import { afterAll, expect, test } from "bun:test";
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const REPO = resolve(import.meta.dir, "..");
const TOKEN = "ab".repeat(20);
const HOME = mkdtempSync(join(tmpdir(), "pz decode $home-"));
const WORK = mkdtempSync(join(tmpdir(), "pz decode $work-"));
const LOG = join(WORK, "fake.log");
writeFileSync(LOG, "");
afterAll(() => {
  rmSync(HOME, { recursive: true, force: true });
  rmSync(WORK, { recursive: true, force: true });
});

const STUB = [
  "import sys",
  "sys.path.insert(0, 'scripts')",
  "import decode_polaris_token as d",
  `d.scan_for_token = lambda p, verbose=False: '${TOKEN}'`,
  "sys.argv = ['decode_polaris_token.py'] + sys.argv[1:]",
  "sys.exit(d.main())",
].join("\n");

function run(...args: string[]) {
  const p = Bun.spawnSync(["python3", "-I", "-B", "-c", STUB, ...args], {
    cwd: REPO,
    env: { HOME, PATH: process.env.PATH ?? "/usr/bin:/bin", PYTHONDONTWRITEBYTECODE: "1" },
  });
  return { code: p.exitCode, out: p.stdout.toString(), err: p.stderr.toString() };
}

// Every file under the repo (including .git and ignored files) as
// "path sha256", sorted: any created, deleted or rewritten file changes it.
function repoState(): string {
  const lines: string[] = [];
  const walk = (dir: string) => {
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      const p = join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.isFile()) lines.push(`${p} ${new Bun.CryptoHasher("sha256").update(readFileSync(p)).digest("hex")}`);
      else lines.push(`${p} <${e.isSymbolicLink() ? "symlink" : "other"}>`);
    }
  };
  walk(REPO);
  return lines.sort().join("\n");
}

test("prints the token and writes nothing to HOME, beside the log, or into the repo", () => {
  const before = repoState();
  const r = run(LOG);
  expect(r.code).toBe(0);
  expect(r.out).toContain(TOKEN);
  expect(readdirSync(HOME)).toEqual([]);
  expect(readdirSync(WORK)).toEqual(["fake.log"]);
  expect(repoState()).toBe(before);
});

test("the old --out and --board-name options are gone", () => {
  expect(run(LOG, "--out", join(WORK, "t")).code).toBe(2);
  expect(run(LOG, "--board-name", "owTEST").code).toBe(2);
  expect(readdirSync(WORK)).toEqual(["fake.log"]);
});
