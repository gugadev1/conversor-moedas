const form = document.querySelector("#converter-form");
const fromSelect = document.querySelector("#from");
const toSelect = document.querySelector("#to");
const amountInput = document.querySelector("#amount");
const convertButton = document.querySelector("#convert-button");
const resultValue = document.querySelector("#result-value");
const resultNote = document.querySelector("#result-note");
const liveRatePair = document.querySelector("#live-rate-pair");
const liveRateValue = document.querySelector("#live-rate-value");
const liveRateNote = document.querySelector("#live-rate-note");
const themeToggle = document.querySelector("#theme-toggle");

const LIVE_RATE_INTERVAL_MS = 10000;

let liveRateIntervalId = null;
let isLiveRateRequestRunning = false;

const THEME_STORAGE_KEY = "conversor-theme";

function applyTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  if (themeToggle) {
    const isDark = theme === "dark";
    themeToggle.checked = isDark;
    themeToggle.setAttribute("aria-label", isDark ? "Desativar modo escuro" : "Ativar modo escuro");
  }
}

function resolveInitialTheme() {
  const saved = localStorage.getItem(THEME_STORAGE_KEY);
  if (saved === "dark" || saved === "light") {
    return saved;
  }

  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function readSavedThemePreference() {
  const saved = localStorage.getItem(THEME_STORAGE_KEY);
  return saved === "dark" || saved === "light" ? saved : null;
}

async function parseJsonSafely(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function setupThemeToggle() {
  const initialTheme = resolveInitialTheme();
  applyTheme(initialTheme);

  const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  mediaQuery.addEventListener("change", (event) => {
    if (readSavedThemePreference() !== null) {
      return;
    }

    applyTheme(event.matches ? "dark" : "light");
  });

  if (!themeToggle) {
    return;
  }

  themeToggle.addEventListener("change", () => {
    const next = themeToggle.checked ? "dark" : "light";
    localStorage.setItem(THEME_STORAGE_KEY, next);
    applyTheme(next);
  });
}

function buildCurrencyOption(currency) {
  const option = document.createElement("option");
  option.value = currency.code;
  option.textContent = currency.name ? `${currency.code} - ${currency.name}` : currency.code;
  return option;
}

function populateCurrencySelects(currencies) {
  const uniqueCodes = new Set();
  const normalized = currencies.filter((currency) => {
    if (!currency || typeof currency.code !== "string") {
      return false;
    }

    const code = currency.code.trim().toUpperCase();
    if (!code || uniqueCodes.has(code)) {
      return false;
    }

    uniqueCodes.add(code);
    currency.code = code;
    currency.name = typeof currency.name === "string" ? currency.name.trim() : "";
    return true;
  });

  if (!normalized.length) {
    return;
  }

  const previousFrom = fromSelect.value || "USD";
  const previousTo = toSelect.value || "BRL";

  fromSelect.innerHTML = "";
  toSelect.innerHTML = "";

  normalized.forEach((currency) => {
    fromSelect.appendChild(buildCurrencyOption(currency));
    toSelect.appendChild(buildCurrencyOption(currency));
  });

  fromSelect.value = uniqueCodes.has(previousFrom) ? previousFrom : (uniqueCodes.has("USD") ? "USD" : normalized[0].code);
  toSelect.value = uniqueCodes.has(previousTo) ? previousTo : (uniqueCodes.has("BRL") ? "BRL" : normalized[0].code);
}

async function loadSupportedCurrencies() {
  fromSelect.disabled = true;
  toSelect.disabled = true;

  try {
    const response = await fetch("/api/currencies", {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
    });

    const data = await parseJsonSafely(response);
    if (!response.ok || !data || !Array.isArray(data.currencies)) {
      return;
    }

    populateCurrencySelects(data.currencies);
  } catch {
    // Keeps default static options if currency endpoint is unavailable.
  } finally {
    fromSelect.disabled = false;
    toSelect.disabled = false;
  }
}

function formatCurrency(value, currencyCode) {
  try {
    return new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: currencyCode,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${value.toFixed(2)} ${currencyCode}`;
  }
}

function setLoading(isLoading) {
  convertButton.disabled = isLoading;
  convertButton.textContent = isLoading ? "Convertendo..." : "Converter";
}

function showError(message) {
  resultValue.textContent = "Erro";
  resultValue.classList.add("result-value--error");
  resultNote.textContent = message;
  resultNote.classList.add("result-note--error");
}

function clearErrorStyles() {
  resultValue.classList.remove("result-value--error");
  resultNote.classList.remove("result-note--error");
}

function formatTime(isoDate) {
  if (!isoDate) {
    return "agora";
  }

  const parsedDate = new Date(isoDate);
  if (Number.isNaN(parsedDate.getTime())) {
    return "agora";
  }

  return parsedDate.toLocaleTimeString("pt-BR");
}

function resetLiveRateErrorStyles() {
  liveRateValue.classList.remove("live-rate-value--error");
  liveRateNote.classList.remove("result-note--error");
}

function showLiveRateError(message) {
  liveRateValue.textContent = "Erro";
  liveRateValue.classList.add("live-rate-value--error");
  liveRateNote.textContent = message;
  liveRateNote.classList.add("result-note--error");
}

async function refreshLiveRate() {
  if (isLiveRateRequestRunning) {
    return;
  }

  isLiveRateRequestRunning = true;
  resetLiveRateErrorStyles();

  const from = fromSelect.value;
  const to = toSelect.value;

  liveRatePair.textContent = `${from} -> ${to}`;
  liveRateNote.textContent = "Atualizando cotacao...";

  try {
    const response = await fetch(
      `/api/rate?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
        },
      },
    );

    const data = await parseJsonSafely(response);
    if (!response.ok) {
      showLiveRateError(data?.message || "Falha ao obter cotacao atual.");
      return;
    }

    if (!data || typeof data.conversionRate !== "number") {
      showLiveRateError("Resposta invalida ao obter cotacao atual.");
      return;
    }

    resetLiveRateErrorStyles();
    liveRateValue.textContent = Number(data.conversionRate).toFixed(6);
    liveRateNote.textContent = `Atualizado as ${formatTime(data.updatedAt)}`;
  } catch {
    showLiveRateError("Nao foi possivel atualizar a cotacao.");
  } finally {
    isLiveRateRequestRunning = false;
  }
}

function startLiveRatePolling() {
  if (liveRateIntervalId !== null) {
    clearInterval(liveRateIntervalId);
  }

  liveRateIntervalId = setInterval(() => {
    if (document.visibilityState === "visible") {
      refreshLiveRate();
    }
  }, LIVE_RATE_INTERVAL_MS);
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearErrorStyles();

  const from = fromSelect.value;
  const to = toSelect.value;
  const amount = Number(amountInput.value);

  if (!Number.isFinite(amount) || amount <= 0) {
    showError("Informe um valor valido maior que zero.");
    return;
  }

  setLoading(true);
  resultValue.textContent = "...";
  resultNote.textContent = "Consultando taxa em tempo real.";

  try {
    const response = await fetch("/api/convert", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ from, to, amount }),
    });

    const data = await parseJsonSafely(response);

    if (!response.ok) {
      const errorMessage = data?.message || "Falha ao converter moedas.";
      showError(errorMessage);
      return;
    }

    if (!data || typeof data.conversionResult !== "number" || typeof data.conversionRate !== "number") {
      showError("Resposta invalida ao converter moedas.");
      return;
    }

    clearErrorStyles();
    resultValue.textContent = formatCurrency(data.conversionResult, data.targetCode);
    resultNote.textContent = `Taxa ${data.baseCode} -> ${data.targetCode}: ${Number(data.conversionRate).toFixed(6)}`;
  } catch {
    showError("Nao foi possivel conectar ao backend.");
  } finally {
    setLoading(false);
  }
});

fromSelect.addEventListener("change", () => {
  refreshLiveRate();
});

toSelect.addEventListener("change", () => {
  refreshLiveRate();
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    refreshLiveRate();
  }
});

async function initializeApp() {
  setupThemeToggle();
  await loadSupportedCurrencies();
  refreshLiveRate();
  startLiveRatePolling();
}

initializeApp();
