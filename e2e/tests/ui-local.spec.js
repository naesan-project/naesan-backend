import {expect, test} from '@playwright/test';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const BACKEND_ROOT = fileURLToPath(new URL('../../', import.meta.url));
const FRONTEND_CONTEXT = process.env.NAESAN_FRONTEND_CONTEXT ??
  '../naesan-frontend';
const RECEIPT_PATH = path.resolve(
    BACKEND_ROOT,
    FRONTEND_CONTEXT,
    'src/assets/evidence/purchase-record.png',
);
const PASSWORD = 'correct horse battery staple';

test('React UI에서 가입부터 패스 공유 폐기까지 완료한다', async ({
  context,
  page,
}) => {
  const email = `ui-smoke-${crypto.randomUUID()}@example.com`;

  await page.goto('/signup');
  await page.getByLabel('이메일').fill(email);
  await page.locator('#signup-password').fill(PASSWORD);
  await page.getByRole('button', {name: '회원가입'}).click();
  await expect(page.getByRole('heading', {
    name: '계정을 만들었어요',
  })).toBeVisible();

  await page.getByRole('button', {name: '로그인하기'}).click();
  await page.getByLabel('이메일').fill(email);
  await page.locator('#login-password').fill(PASSWORD);
  await page.getByRole('button', {name: '로그인', exact: true}).click();
  await expect(page.getByRole('heading', {name: '내 패스'})).toBeVisible();

  await page.getByRole('button', {name: '새 패스 만들기'}).click();
  await page.getByRole('button', {name: '다음'}).click();
  await page.getByRole('button', {name: '사진 등록하기'}).click();

  await page.getByRole('button', {name: '촬영 시작'}).click();
  for (let index = 0; index < 6; index += 1) {
    await page.getByRole('button', {name: '촬영', exact: true}).click();
    await page.getByRole('button', {name: '이 사진 사용'}).click();
    await page.getByRole('button', {name: '다음 항목'}).click();
  }
  await page.getByRole('button', {name: '구매 증빙 등록하기'}).click();

  await page.getByLabel('구매 증빙 파일').setInputFiles(RECEIPT_PATH);
  await expect(page.getByText('파일 선택 완료')).toBeVisible();
  await page.getByRole('button', {name: '최종 확인하기'}).click();
  await expect(page.getByRole('heading', {
    name: '등록할 내용을 확인해주세요',
  })).toBeVisible();

  await page.getByRole('checkbox', {
    name: '위 내용을 확인했어요.',
  }).check();
  await page.getByRole('button', {
    name: '정보 확정하고 패스 만들기',
  }).click();
  await page.getByRole('dialog').getByRole('button', {
    name: '패스 만들기',
  }).click();

  await expect(page.getByRole('heading', {
    name: '패스를 만들었어요',
  })).toBeVisible({timeout: 30_000});
  await page.getByRole('button', {name: '발급한 패스 보기'}).click();
  await expect(page.getByRole('heading', {name: 'Arco 28 Tote'})).toBeVisible();

  await expect.poll(async () => {
    await page.reload();
    return page.getByRole('region', {
      name: '외부 검증 상태',
    }).getByRole('strong').textContent();
  }, {
    intervals: [1_000, 2_000, 3_000],
    timeout: 30_000,
  }).toBe('외부 검증 완료');

  await page.getByRole('button', {name: '공유 링크 만들기'}).click();
  await page.getByRole('dialog').getByRole('button', {
    name: '링크 만들기',
  }).click();
  const shareUrl = await page.locator('code').innerText();

  const publicPage = await context.newPage();
  await publicPage.goto(shareUrl);
  await expect(publicPage.getByRole('heading', {
    name: 'Arco 28 Tote',
  })).toBeVisible();
  await expect(publicPage.getByRole('region', {
    name: '외부 검증 상태',
  })).toContainText('외부 검증 완료');

  await page.getByRole('button', {name: '공유 중지'}).click();
  await page.getByRole('alertdialog').getByRole('button', {
    name: '공유 중지',
  }).click();
  await expect(page.getByRole('status')).toContainText('공유를 중지했어요.');

  await publicPage.reload();
  await expect(publicPage.getByRole('heading', {
    name: '사용할 수 없는 공유 링크예요',
  })).toBeVisible();
});
