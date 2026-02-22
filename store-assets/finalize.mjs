import sharp from 'sharp';
import { cp, mkdir } from 'fs/promises';
import path from 'path';

const STORE_DIR = '/home/twosb7/projects/dogakdogak/store-assets';
const FASTLANE_IMG = '/home/twosb7/projects/dogakdogak/fastlane/metadata/android/en-US/images';
const ICON_SRC = '/home/twosb7/projects/dogakdogak/app/src/main/res/drawable/dogakdogak_icon.webp';

async function createIcon() {
  // Google Play requires 512x512 PNG
  await sharp(ICON_SRC)
    .resize(512, 512, { fit: 'contain', background: { r: 255, g: 255, b: 255, alpha: 0 } })
    .png()
    .toFile(path.join(FASTLANE_IMG, 'icon.png'));
  console.log('Icon: 512x512 -> icon.png');
}

async function createFeatureGraphic() {
  // Google Play requires 1024x500
  const width = 1024;
  const height = 500;

  // Create background with app's warm color
  const bg = await sharp({
    create: { width, height, channels: 3, background: { r: 253, g: 245, b: 240 } },
  }).png().toBuffer();

  // Resize icon for feature graphic
  const iconBuf = await sharp(ICON_SRC)
    .resize(280, 280, { fit: 'contain', background: { r: 253, g: 245, b: 240, alpha: 1 } })
    .png()
    .toBuffer();

  // Create text SVG
  const svg = Buffer.from(`
    <svg width="${width}" height="${height}">
      <text x="620" y="180"
            font-family="HS산토끼체" font-size="72"
            fill="#5C3D2E" text-anchor="middle">도각도각</text>
      <text x="620" y="260"
            font-family="HS산토끼체" font-size="36"
            fill="#9B7B6B" text-anchor="middle">ASMR 키보드 사운드</text>
      <text x="620" y="340"
            font-family="HS산토끼체" font-size="28"
            fill="#C4A08E" text-anchor="middle">타건음 · 콤보 · 랭킹 · 이펙트</text>
    </svg>`);

  const textBuf = await sharp(svg).resize(width, height).png().toBuffer();

  await sharp(bg)
    .composite([
      { input: iconBuf, top: 110, left: 80 },
      { input: textBuf, top: 0, left: 0 },
    ])
    .png()
    .toFile(path.join(FASTLANE_IMG, 'featureGraphic.png'));

  console.log('Feature Graphic: 1024x500 -> featureGraphic.png');
}

async function copyScreenshots() {
  // Phone screenshots
  const phoneDir = path.join(FASTLANE_IMG, 'phoneScreenshots');
  await mkdir(phoneDir, { recursive: true });
  // Remove old screenshots first
  const { readdirSync, unlinkSync } = await import('fs');
  for (const f of readdirSync(phoneDir)) {
    unlinkSync(path.join(phoneDir, f));
  }
  for (let i = 1; i <= 5; i++) {
    await cp(
      path.join(STORE_DIR, 'phone', `${i}.png`),
      path.join(phoneDir, `${i}.png`)
    );
  }
  console.log('Phone screenshots: 5 files copied');

  // 7-inch tablet screenshots
  const tab7Dir = path.join(FASTLANE_IMG, 'sevenInchScreenshots');
  await mkdir(tab7Dir, { recursive: true });
  for (let i = 1; i <= 5; i++) {
    await cp(
      path.join(STORE_DIR, 'tablet7', `${i}.png`),
      path.join(tab7Dir, `${i}.png`)
    );
  }
  console.log('7-inch tablet screenshots: 5 files copied');

  // 10-inch tablet screenshots
  const tab10Dir = path.join(FASTLANE_IMG, 'tenInchScreenshots');
  await mkdir(tab10Dir, { recursive: true });
  for (let i = 1; i <= 5; i++) {
    await cp(
      path.join(STORE_DIR, 'tablet10', `${i}.png`),
      path.join(tab10Dir, `${i}.png`)
    );
  }
  console.log('10-inch tablet screenshots: 5 files copied');
}

async function main() {
  await createIcon();
  await createFeatureGraphic();
  await copyScreenshots();
  console.log('\nAll store assets finalized!');
}

main().catch(console.error);
