import {expect, test} from '@playwright/test';

import {
  createPassport,
  openClient,
  registerAndLogin,
} from '../support/scenario.js';

test('fresh local에서 가입부터 새 보유자의 공개 검증까지 핵심 흐름을 완료한다', async ({
  browser,
}, testInfo) => {
  const ownerContext = await browser.newContext();
  const recipientContext = await browser.newContext();
  const anonymousContext = await browser.newContext();
  const ownerPage = await ownerContext.newPage();
  const recipientPage = await recipientContext.newPage();
  const anonymousPage = await anonymousContext.newPage();
  const fixtureId = crypto.randomUUID();
  const ownerEmail = `core-owner-${fixtureId}@example.com`;
  const recipientEmail = `core-recipient-${fixtureId}@example.com`;

  await Promise.all([
    openClient(ownerPage),
    openClient(recipientPage),
    openClient(anonymousPage),
  ]);
  const owner = await registerAndLogin(ownerPage, ownerEmail);
  const recipient = await registerAndLogin(recipientPage, recipientEmail);
  expect(owner.account.id).not.toBe(recipient.account.id);

  const issued = await createPassport(ownerPage);
  const evidence = await ownerPage.evaluate(
      (evidenceId) => window.naesan.evidenceDetails(evidenceId),
      issued.evidence.id,
  );
  expect(evidence.body.state).toBe('CONFIRMED');
  expect(evidence.body.file.state).toBe('PROMOTED');

  const passportId = issued.passport.id;
  await expect.poll(
      () => ownerPage.evaluate(
          (id) => window.naesan.passportDetails(id),
          passportId,
      ).then((response) => response.body.proof.state),
      {
        message: 'local proof worker가 Passport proof를 확정한다',
        timeout: 15_000,
      },
  ).toBe('CONFIRMED');

  const oldShare = await ownerPage.evaluate(
      (id) => window.naesan.issueShare(id),
      passportId,
  );
  const initialPublicView = await anonymousPage.evaluate(
      (rawToken) => window.naesan.verifyShare(rawToken),
      oldShare.body.rawToken,
  );
  expect(initialPublicView.body).toEqual(expect.objectContaining({
    capability: 'SUMMARY',
    productName: '생각등대',
    passportStatus: 'ACTIVE',
    trustStage: 'ANCHOR_CONFIRMED',
  }));
  expect(initialPublicView.body).not.toHaveProperty('merchantName');
  expect(initialPublicView.body).not.toHaveProperty('serialNumber');
  expect(initialPublicView.body).not.toHaveProperty('snapshotDigest');

  const transfer = await ownerPage.evaluate(
      ({id, email}) => window.naesan.createTransfer(id, email),
      {id: passportId, email: recipientEmail},
  );
  await recipientPage.evaluate(
      (requestId) => window.naesan.acceptTransfer(requestId),
      transfer.body.id,
  );

  const previousHolderView = await ownerPage.evaluate(
      (id) => window.naesan.passportDetailsOutcome(id),
      passportId,
  );
  const anonymousProtectedView = await anonymousPage.evaluate(
      () => window.naesan.passportListOutcome(),
  );
  expect(previousHolderView.status).toBe(404);
  expect(previousHolderView.body.code).toBe('PASSPORT_NOT_FOUND');
  expect(anonymousProtectedView.status).toBe(401);

  const history = await recipientPage.evaluate(
      (id) => window.naesan.ownershipHistory(id),
      passportId,
  );
  expect(history.body).toHaveLength(2);
  expect(history.body[1]).toEqual(expect.objectContaining({
    previousHolderAccountId: owner.account.id,
    newHolderAccountId: recipient.account.id,
    reason: 'TRANSFERRED',
  }));

  const revokedShareView = await anonymousPage.evaluate(
      (rawToken) => window.naesan.verifyShareOutcome(rawToken),
      oldShare.body.rawToken,
  );
  expect(revokedShareView.status).toBe(404);
  expect(revokedShareView.body.code).toBe('PUBLIC_SHARE_NOT_FOUND');

  const newShare = await recipientPage.evaluate(
      (id) => window.naesan.issueShare(id),
      passportId,
  );
  const newHolderPublicView = await anonymousPage.evaluate(
      (rawToken) => window.naesan.verifyShare(rawToken),
      newShare.body.rawToken,
  );
  expect(newHolderPublicView.body).toEqual(expect.objectContaining({
    productName: '생각등대',
    passportStatus: 'ACTIVE',
    trustStage: 'ANCHOR_CONFIRMED',
  }));

  await anonymousPage.evaluate(() => {
    const status = document.querySelector('#status');
    if (!status) {
      return;
    }
    status.textContent = [
      'signup: completed',
      'evidence: confirmed',
      'proof: confirmed',
      'transfer: accepted',
      'old share: revoked',
      'new share: verified',
    ].join('\n');
  });
  await anonymousPage.screenshot({
    path: testInfo.outputPath('core-local-success.png'),
    fullPage: true,
  });

  await Promise.all([
    ownerContext.close(),
    recipientContext.close(),
    anonymousContext.close(),
  ]);
});
