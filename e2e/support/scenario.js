export const PASSWORD = 'correct horse battery staple';

export async function openClient(page) {
  await page.goto('/');
  await page.waitForFunction(() => window.naesan !== undefined);
}

export async function registerAndLogin(page, email) {
  return page.evaluate(async ({accountEmail, password}) => {
    const registered = await window.naesan.register(accountEmail, password);
    const session = await window.naesan.login(accountEmail, password);
    return {
      account: registered.body,
      session: session.body,
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
