import { cp, mkdir, readdir, rm } from 'fs/promises';
import path from 'path';

export async function replaceIndexedPngSet(sourceDir, targetDir, count = 5) {
  await mkdir(targetDir, { recursive: true });
  for (const entry of await readdir(targetDir)) {
    if (entry.endsWith('.png')) {
      await rm(path.join(targetDir, entry), { force: true });
    }
  }
  for (let i = 1; i <= count; i++) {
    await cp(
      path.join(sourceDir, `${i}.png`),
      path.join(targetDir, `${i}.png`)
    );
  }
}
