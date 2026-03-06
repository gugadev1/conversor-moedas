const form = document.querySelector("#converter-form");
const fromSelect = document.querySelector("#from");
const toSelect = document.querySelector("#to");
const amountInput = document.querySelector("#amount");
const convertButton = document.querySelector("#convert-button");
const resultValue = document.querySelector("#result-value");
const resultNote = document.querySelector("#result-note");

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
