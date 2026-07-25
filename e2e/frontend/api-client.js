const API_URL = 'http://localhost:8080/api';
const JSON_HEADERS = {
  'Content-Type': 'application/json',
};

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

class ApiError extends Error {
  constructor(status, body) {
    super(`API request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

window.naesan = {
  csrfToken,
  request,
  mutation,
  jsonHeaders: JSON_HEADERS,
};
