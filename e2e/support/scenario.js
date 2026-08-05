import {fileURLToPath} from 'node:url';

export const PASSWORD = 'correct horse battery staple';
const API_CLIENT_PATH = fileURLToPath(
    new URL('../frontend/api-client.js', import.meta.url),
);

export async function openClient(page) {
  await page.goto('/');
  if (new URL(page.url()).port !== '5173') {
    await page.addScriptTag({path: API_CLIENT_PATH});
  }
  await page.waitForFunction(() => window.naesan !== undefined);
}

export async function registerAndLogin(page, email) {
  return page.evaluate(async ({accountEmail, password}) => {
    const registered = await window.naesan.register(accountEmail, password);
    const tokenSession = await window.naesan.login(accountEmail, password);
    return {
      account: registered.body,
      tokenSession: tokenSession.body,
    };
  }, {
    accountEmail: email,
    password: PASSWORD,
  });
}

export async function createPassport(page) {
  return page.evaluate(async () => {
    const evidence = await window.naesan.createEvidence({
      merchantName: '생각상점',
      productName: '생각등대',
      serialNumber: 'NAESAN-E2E-001',
      purchasedAt: '2026-07-25',
      amount: 1000,
      currency: 'KRW',
    });
    await window.naesan.attachEvidenceFile(evidence.body.id);
    const confirmation = await window.naesan.confirmEvidence(evidence.body.id);
    const passport = await window.naesan.issuePassport(
        confirmation.body.snapshotId,
    );
    return {
      evidence: evidence.body,
      passport: passport.body,
    };
  });
}
