const API_URL = 'http://localhost:8080/api';
const EVIDENCE_FILE_BASE64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: 'include',
    ...options,
  });
  const body = await responseBody(response);
  if (!response.ok) {
    throw new ApiError(response.status, body);
  }
  return {
    body,
    headers: Object.fromEntries(response.headers.entries()),
    status: response.status,
  };
}

async function responseBody(response) {
  if (response.status === 204) {
    return null;
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

async function csrfToken() {
  await request('/csrf');
  const csrfCookie = document.cookie
      .split('; ')
      .find((cookie) => cookie.startsWith('XSRF-TOKEN='));
  if (!csrfCookie) {
    throw new Error('XSRF-TOKEN cookie was not issued');
  }
  return decodeURIComponent(csrfCookie.split('=').slice(1).join('='));
}

async function mutation(path, method, body, headers = {}) {
  const token = await csrfToken();
  const requestHeaders = {
    'X-XSRF-TOKEN': token,
    ...headers,
  };
  if (body !== undefined && !(body instanceof FormData)) {
    requestHeaders['Content-Type'] = 'application/json';
  }
  return request(path, {
    method,
    headers: requestHeaders,
    body: body instanceof FormData ? body : JSON.stringify(body),
  });
}

async function register(email, password) {
  return request('/accounts', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({email, password}),
  });
}

async function login(email, password) {
  return mutation('/sessions', 'POST', {email, password});
}

async function createEvidence(metadata) {
  return mutation('/evidence', 'POST', metadata);
}

async function attachEvidenceFile(evidenceId) {
  const bytes = Uint8Array.from(
      atob(EVIDENCE_FILE_BASE64),
      (character) => character.charCodeAt(0),
  );
  const form = new FormData();
  form.append('file', new Blob([bytes], {type: 'image/png'}), 'evidence.png');
  return mutation(`/evidence/${evidenceId}/file`, 'POST', form);
}

async function confirmEvidence(evidenceId) {
  return mutation(`/evidence/${evidenceId}/confirm`, 'POST');
}

async function issuePassport(snapshotId) {
  return mutation('/passports', 'POST', {snapshotId});
}

async function passportList() {
  return request('/passports');
}

async function passportDetails(passportId) {
  return request(`/passports/${passportId}`);
}

async function ownershipHistory(passportId) {
  return request(`/passports/${passportId}/ownership-history`);
}

async function issueShare(passportId) {
  return mutation(
      `/passports/${passportId}/shares`,
      'POST',
      {capability: 'SUMMARY'},
  );
}

async function verifyShare(rawToken) {
  return request('/public/passport-verification', {
    headers: {'X-Public-Share-Token': rawToken},
  });
}

async function verifyShareOutcome(rawToken) {
  try {
    return await verifyShare(rawToken);
  } catch (error) {
    if (!(error instanceof ApiError)) {
      throw error;
    }
    return {
      body: error.body,
      status: error.status,
    };
  }
}

async function createTransfer(passportId, recipientEmail) {
  return mutation(
      `/passports/${passportId}/transfers`,
      'POST',
      {recipientEmail},
  );
}

async function incomingTransfers() {
  return request('/transfers/incoming');
}

async function outgoingTransfers() {
  return request('/transfers/outgoing');
}

async function acceptTransfer(requestId) {
  return mutation(`/transfers/${requestId}/acceptance`, 'POST');
}

class ApiError extends Error {
  constructor(status, body) {
    super(`API request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

window.naesan = {
  acceptTransfer,
  attachEvidenceFile,
  confirmEvidence,
  createEvidence,
  createTransfer,
  csrfToken,
  incomingTransfers,
  issuePassport,
  issueShare,
  login,
  outgoingTransfers,
  ownershipHistory,
  passportDetails,
  passportList,
  register,
  verifyShare,
  verifyShareOutcome,
};
