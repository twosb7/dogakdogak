import sharp from 'sharp';
import { mkdir } from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const RAW_DIR = process.env.DOGAK_RAW_DIR ?? path.join(SCRIPT_DIR, 'raw');
const PHONE_DIR = process.env.DOGAK_PHONE_DIR ?? path.join(SCRIPT_DIR, 'phone');
const TABLET7_DIR = process.env.DOGAK_TABLET7_DIR ?? path.join(SCRIPT_DIR, 'tablet7');
const TABLET10_DIR = process.env.DOGAK_TABLET10_DIR ?? path.join(SCRIPT_DIR, 'tablet10');

// Google Play requirements:
// Phone: min 320px, max 3840px, 16:9 aspect ratio recommended
// 7-inch tablet: min 320px, max 3840px
// 10-inch tablet: min 320px, max 3840px

const PHONE_WIDTH = 1080;
const PHONE_HEIGHT = 1920;
const TABLET7_WIDTH = 1200;
const TABLET7_HEIGHT = 1920;
const TABLET10_WIDTH = 1600;
const TABLET10_HEIGHT = 2560;

const STATUS_BAR_HEIGHT = 80; // pixels to crop from top (status bar)
const NAV_BAR_HEIGHT = 50;   // pixels to crop from bottom (nav gesture bar)

const screenshots = [
  { file: '1_sound.png', caption: 'ASMR 타건음 선택', subCaption: '16가지 기계식 스위치 사운드' },
  { file: '2_combo.png', caption: '실시간 콤보 시스템', subCaption: '타이핑하면 콤보가 쌓여요!' },
  { file: '3_ranking.png', caption: '글로벌 랭킹', subCaption: '전 세계 유저와 경쟁하세요' },
  { file: '4_effects.png', caption: '콤보 이펙트', subCaption: '프리미엄 · 큐티핑크 · ARCADE' },
  { file: '5_settings.png', caption: '내 프로필 &amp; 설정', subCaption: '테마 · 볼륨 · 통계' },
];

async function createCaptionOverlay(width, height, caption, subCaption) {
  const captionHeight = Math.round(height * 0.12);
  const fontSize = Math.round(captionHeight * 0.35);
  const subFontSize = Math.round(captionHeight * 0.22);

  const svg = `
    <svg width="${width}" height="${captionHeight}">
      <rect width="${width}" height="${captionHeight}" fill="#FDF5F0"/>
      <text x="${width / 2}" y="${captionHeight * 0.42}"
            font-family="HS산토끼체" font-size="${fontSize}"
            fill="#5C3D2E" text-anchor="middle">${caption}</text>
      <text x="${width / 2}" y="${captionHeight * 0.72}"
            font-family="HS산토끼체" font-size="${subFontSize}"
            fill="#9B7B6B" text-anchor="middle">${subCaption}</text>
    </svg>`;
  return { svg: Buffer.from(svg), height: captionHeight };
}

async function processScreenshot(info, index) {
  const inputPath = path.join(RAW_DIR, info.file);
  const meta = await sharp(inputPath).metadata();
  console.log(`Processing ${info.file}: ${meta.width}x${meta.height}`);

  // Crop status bar and nav bar
  const cropped = sharp(inputPath).extract({
    left: 0,
    top: STATUS_BAR_HEIGHT,
    width: meta.width,
    height: meta.height - STATUS_BAR_HEIGHT - NAV_BAR_HEIGHT,
  });

  const croppedBuf = await cropped.toBuffer();

  // --- Phone version ---
  const { svg: captionSvg, height: captionH } = await createCaptionOverlay(
    PHONE_WIDTH, PHONE_HEIGHT, info.caption, info.subCaption
  );

  const screenshotHeight = PHONE_HEIGHT - captionH;
  const resizedScreenshot = await sharp(croppedBuf)
    .resize(PHONE_WIDTH, screenshotHeight, { fit: 'cover', position: 'top' })
    .toBuffer();

  const captionBuf = await sharp(captionSvg)
    .resize(PHONE_WIDTH, captionH)
    .png()
    .toBuffer();

  await sharp({
    create: {
      width: PHONE_WIDTH,
      height: PHONE_HEIGHT,
      channels: 3,
      background: { r: 253, g: 245, b: 240 },
    },
  })
    .composite([
      { input: resizedScreenshot, top: 0, left: 0 },
      { input: captionBuf, top: screenshotHeight, left: 0 },
    ])
    .png()
    .toFile(path.join(PHONE_DIR, `${index + 1}.png`));

  console.log(`  Phone: ${PHONE_WIDTH}x${PHONE_HEIGHT} -> ${PHONE_DIR}/${index + 1}.png`);

  // --- 7-inch Tablet version ---
  const { svg: tab7Svg, height: tab7CaptionH } = await createCaptionOverlay(
    TABLET7_WIDTH, TABLET7_HEIGHT, info.caption, info.subCaption
  );
  const tab7ScreenH = TABLET7_HEIGHT - tab7CaptionH;
  const tab7Screenshot = await sharp(croppedBuf)
    .resize(TABLET7_WIDTH, tab7ScreenH, { fit: 'contain', background: { r: 253, g: 245, b: 240, alpha: 1 } })
    .toBuffer();
  const tab7CaptionBuf = await sharp(tab7Svg).resize(TABLET7_WIDTH, tab7CaptionH).png().toBuffer();

  await sharp({
    create: {
      width: TABLET7_WIDTH,
      height: TABLET7_HEIGHT,
      channels: 3,
      background: { r: 253, g: 245, b: 240 },
    },
  })
    .composite([
      { input: tab7Screenshot, top: 0, left: 0 },
      { input: tab7CaptionBuf, top: tab7ScreenH, left: 0 },
    ])
    .png()
    .toFile(path.join(TABLET7_DIR, `${index + 1}.png`));

  console.log(`  Tablet 7": ${TABLET7_WIDTH}x${TABLET7_HEIGHT} -> ${TABLET7_DIR}/${index + 1}.png`);

  // --- 10-inch Tablet version ---
  const { svg: tab10Svg, height: tab10CaptionH } = await createCaptionOverlay(
    TABLET10_WIDTH, TABLET10_HEIGHT, info.caption, info.subCaption
  );
  const tab10ScreenH = TABLET10_HEIGHT - tab10CaptionH;
  const tab10Screenshot = await sharp(croppedBuf)
    .resize(TABLET10_WIDTH, tab10ScreenH, { fit: 'contain', background: { r: 253, g: 245, b: 240, alpha: 1 } })
    .toBuffer();
  const tab10CaptionBuf = await sharp(tab10Svg).resize(TABLET10_WIDTH, tab10CaptionH).png().toBuffer();

  await sharp({
    create: {
      width: TABLET10_WIDTH,
      height: TABLET10_HEIGHT,
      channels: 3,
      background: { r: 253, g: 245, b: 240 },
    },
  })
    .composite([
      { input: tab10Screenshot, top: 0, left: 0 },
      { input: tab10CaptionBuf, top: tab10ScreenH, left: 0 },
    ])
    .png()
    .toFile(path.join(TABLET10_DIR, `${index + 1}.png`));

  console.log(`  Tablet 10": ${TABLET10_WIDTH}x${TABLET10_HEIGHT} -> ${TABLET10_DIR}/${index + 1}.png`);
}

async function main() {
  await mkdir(PHONE_DIR, { recursive: true });
  await mkdir(TABLET7_DIR, { recursive: true });
  await mkdir(TABLET10_DIR, { recursive: true });

  for (let i = 0; i < screenshots.length; i++) {
    await processScreenshot(screenshots[i], i);
  }

  console.log('\nDone! All screenshots processed.');
}

main().catch(console.error);
