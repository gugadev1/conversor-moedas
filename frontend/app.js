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

function setupThemeToggle() {
  const initialTheme = resolveInitialTheme();
  applyTheme(initialTheme);

  if (!themeToggle) {
    return;
  }

  themeToggle.addEventListener("change", () => {
    const next = themeToggle.checked ? "dark" : "light";
    localStorage.setItem(THEME_STORAGE_KEY, next);
    applyTheme(next);
  });
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

    const data = await response.json();
    if (!response.ok) {
      showLiveRateError(data.message || "Falha ao obter cotacao atual.");
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

    const data = await response.json();

    if (!response.ok) {
      const errorMessage = data.message || "Falha ao converter moedas.";
      showError(errorMessage);
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

setupThemeToggle();
refreshLiveRate();
startLiveRatePolling();
