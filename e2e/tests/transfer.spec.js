import {expect, test} from '@playwright/test';

import {
  createPassport,
  openClient,
  registerAndLogin,
} from '../support/scenario.js';

test('두 사용자가 소유권을 이전하면 이력이 추가되고 기존 share가 폐기된다', async ({
  browser,
}) => {
  const ownerContext = await browser.newContext();
  const recipientContext = await browser.newContext();
  const anonymousContext = await browser.newContext();
  const ownerPage = await ownerContext.newPage();
  const recipientPage = await recipientContext.newPage();
  const anonymousPage = await anonymousContext.newPage();
  const fixtureId = crypto.randomUUID();
  const ownerEmail = `owner-${fixtureId}@example.com`;
  const recipientEmail = `recipient-${fixtureId}@example.com`;

  await Promise.all([
    openClient(ownerPage),
    openClient(recipientPage),
    openClient(anonymousPage),
  ]);
  const owner = await registerAndLogin(ownerPage, ownerEmail);
  const recipient = await registerAndLogin(recipientPage, recipientEmail);
  const issued = await createPassport(ownerPage);
  const passportId = issued.passport.id;
  const share = await ownerPage.evaluate(
      (id) => window.naesan.issueShare(id),
      passportId,
  );

  const publicVerification = await anonymousPage.evaluate(
      (rawToken) => window.naesan.verifyShare(rawToken),
      share.body.rawToken,
  );
  expect(publicVerification.status).toBe(200);

  const transfer = await ownerPage.evaluate(
      ({id, email}) => window.naesan.createTransfer(id, email),
      {id: passportId, email: recipientEmail},
  );
  const incoming = await recipientPage.evaluate(
      () => window.naesan.incomingTransfers(),
  );
  expect(incoming.body).toHaveLength(1);
  expect(incoming.body[0].id).toBe(transfer.body.id);

  const acceptance = await recipientPage.evaluate(
      (requestId) => window.naesan.acceptTransfer(requestId),
      transfer.body.id,
  );
  expect(acceptance.status).toBe(204);

  const ownerPassports = await ownerPage.evaluate(
      () => window.naesan.passportList(),
  );
  const recipientPassports = await recipientPage.evaluate(
      () => window.naesan.passportList(),
  );
  expect(ownerPassports.body).toHaveLength(0);
  expect(recipientPassports.body).toHaveLength(1);
  expect(recipientPassports.body[0].id).toBe(passportId);

  const history = await recipientPage.evaluate(
      (id) => window.naesan.ownershipHistory(id),
      passportId,
  );
  expect(history.body).toEqual([
    expect.objectContaining({
      previousHolderAccountId: null,
      newHolderAccountId: owner.account.id,
      reason: 'ISSUED',
    }),
    expect.objectContaining({
      previousHolderAccountId: owner.account.id,
      newHolderAccountId: recipient.account.id,
      reason: 'TRANSFERRED',
    }),
  ]);

  const oldShare = await anonymousPage.evaluate(
      (rawToken) => window.naesan.verifyShareOutcome(rawToken),
      share.body.rawToken,
  );
  expect(oldShare.status).toBe(404);
  expect(oldShare.body.code).toBe('PUBLIC_SHARE_NOT_FOUND');

  await Promise.all([
    ownerContext.close(),
    recipientContext.close(),
    anonymousContext.close(),
  ]);
});
