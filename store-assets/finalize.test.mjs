import test from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import path from 'node:path';
import { mkdtemp, mkdir, readdir, writeFile } from 'node:fs/promises';

import { replaceIndexedPngSet } from './finalize-lib.mjs';

test('replaceIndexedPngSet removes stale png files before copying', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'dogak-finalize-'));
  const sourceDir = path.join(root, 'source');
  const targetDir = path.join(root, 'target');
  await mkdir(sourceDir, { recursive: true });
  await mkdir(targetDir, { recursive: true });

  for (let i = 1; i <= 5; i++) {
    await writeFile(path.join(sourceDir, `${i}.png`), `new-${i}`);
  }
  await writeFile(path.join(targetDir, 'stale.png'), 'old');

  await replaceIndexedPngSet(sourceDir, targetDir, 5);

  const files = (await readdir(targetDir)).sort();
  assert.deepEqual(files, ['1.png', '2.png', '3.png', '4.png', '5.png']);
});
