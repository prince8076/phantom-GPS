const invoke =
  window.__TAURI__ && window.__TAURI__.core ? window.__TAURI__.core.invoke : null;

const state = {
  report: null,
  context: "",
  messages: [],
  settings: null,
  leftTab: "overview",
  centerTab: "disasm",
  disasmFilter: "all",
  disasmSearch: "",
  importFilter: "",
  stringFilter: "",
  importLimit: 400,
  stringLimit: 300,
  busy: false,
  typing: false,
};

const $ = (selector) => document.querySelector(selector);

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

function fmtBytes(value) {
  const size = Number(value) || 0;
  if (size < 1024) return `${size} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let amount = size / 1024;
  let index = 0;
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024;
    index += 1;
  }
  return `${amount.toFixed(1)} ${units[index]}`;
}

function fmtAddr(value) {
  if (value === null || value === undefined) return "n/a";
  return `0x${Number(value).toString(16).padStart(8, "0")}`;
}

function fmtEntropy(value) {
  return Number(value || 0).toFixed(2);
}

function fmtTimestamp(value) {
  if (!value) return "unknown";
  const date = new Date(Number(value) * 1000);
  return Number.isNaN(date.getTime()) ? "unknown" : date.toLocaleString();
}

function humanError(error) {
  if (typeof error === "string") return error;
  if (error && error.message) return error.message;
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}

let toastTimer;
function toast(message) {
  const node = $("#toast");
  node.textContent = String(message);
  node.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => node.classList.remove("show"), 3600);
}

function setStatus(left, right) {
  if (left) $("#status-left").textContent = left;
  if (right) $("#status-right").textContent = right;
}

async function copyText(text, label) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(String(text));
    toast(`${label || "Value"} copied.`);
  } catch {
    const area = document.createElement("textarea");
    area.value = String(text);
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    try {
      document.execCommand("copy");
      toast(`${label || "Value"} copied.`);
    } catch {
      toast("Clipboard is unavailable.");
    }
    document.body.removeChild(area);
  }
}

function requireNative() {
  if (invoke) return true;
  toast("Launch ReaCopilot with `cargo run` to use native file access.");
  return false;
}

function emptyState(kicker, title, text, hint) {
  const wrap = el("div", "empty-state");
  wrap.append(el("div", "empty-kicker", kicker));
  wrap.append(el("div", "empty-title", title));
  wrap.append(el("div", "empty-text", text));
  if (hint) wrap.append(el("div", "empty-hint", hint));
  return wrap;
}

function updateButtons() {
  const has = Boolean(state.report);
  $("#ai-analyze-btn").disabled = !has || state.busy;
  $("#export-btn").disabled = !has;
  $("#send-btn").disabled = !has || state.busy;
  $("#chat-input").disabled = !has;
}

function applySettings() {
  const badge = $("#model-badge");
  const settings = state.settings;
  if (settings && settings.model) {
    let host = "";
    try {
      host = new URL(settings.baseUrl).host;
    } catch {
      host = "";
    }
    badge.textContent = host ? `${settings.model} - ${host}` : settings.model;
    badge.classList.add("ready");
    badge.classList.remove("muted");
    setStatus(
      $("#status-left").textContent,
      `AI: ${settings.model} - files stay on this machine`
    );
  } else {
    badge.textContent = "not configured";
    badge.classList.add("muted");
    badge.classList.remove("ready");
  }
}

function applySettingsToForm() {
  const settings = state.settings || {};
  $("#set-base").value = settings.baseUrl || "https://api.openai.com/v1";
  $("#set-model").value = settings.model || "gpt-4o-mini";
  $("#set-key").value = settings.apiKey || "";
  $("#set-tokens").value = settings.maxTokens || 2048;
  $("#set-temp").value =
    settings.temperature === undefined || settings.temperature === null
      ? 0.2
      : settings.temperature;
}

function openSettings() {
  applySettingsToForm();
  const dialog = $("#settings-dialog");
  if (typeof dialog.showModal === "function") {
    dialog.showModal();
  } else {
    dialog.setAttribute("open", "");
  }
}

function showOverlay(visible, title, detail) {
  const overlay = $("#overlay");
  if (visible) {
    if (title) $("#overlay-title").textContent = title;
    if (detail) $("#overlay-detail").textContent = detail;
    overlay.classList.remove("hidden");
  } else {
    overlay.classList.add("hidden");
  }
}

async function openBinary() {
  if (!requireNative() || state.busy) return;
  try {
    const path = await invoke("browse_binary");
    if (path) await analyze(path);
  } catch (error) {
    toast(humanError(error));
  }
}

async function analyze(path) {
  if (!invoke || state.busy) return;
  state.busy = true;
  updateButtons();
  showOverlay(
    true,
    "Analyzing binary",
    "Parsing headers, sections, imports, strings and executable code."
  );
  setStatus(`Analyzing ${path}...`, "Static analysis in progress");

  try {
    const report = await invoke("analyze_file", { path });
    state.context = await invoke("build_ai_context", { report });
    state.report = report;
    state.messages = [];
    state.leftTab = "overview";
    state.centerTab = "disasm";
    state.disasmFilter = "all";
    state.disasmSearch = "";
    state.importFilter = "";
    state.stringFilter = "";
    state.importLimit = 400;
    state.stringLimit = 300;

    $("#disasm-search").value = "";
    $("#disasm-filter").value = "all";
    $("#file-name").textContent = report.name;
    $("#file-size").textContent = fmtBytes(report.size);
    $("#file-pill").classList.remove("hidden");
    document
      .querySelectorAll("#left-tabs .tab")
      .forEach((tab) => tab.classList.toggle("active", tab.dataset.tab === "overview"));

    const trimmedPath = report.path;
    const high = report.indicators.filter((item) => item.severity === "high").length;
    state.messages = [
      {
        role: "assistant",
        content:
          `Analyzed ${report.name}.\n\n` +
          `${report.fileType}, ${report.bits ? `${report.bits}-bit ` : ""}${report.arch}. ` +
          `${report.importCount} imports, ${report.strings.length} extracted strings, ` +
          `${report.disassembly.length} decoded instructions and ${report.callTargets.length} call targets.\n\n` +
          `${high} high-severity heuristic finding${high === 1 ? "" : "s"}. ` +
          `Ask about the entry point, imports, notable strings, call targets, or where to dig next.`,
      },
    ];

    renderAll();
    setStatus(
      `${report.name}: ${report.disassembly.length.toLocaleString()} instructions, ` +
        `${report.importCount} imports, ${high} high-severity finding${high === 1 ? "" : "s"}.`,
      `${report.arch} - ${fmtBytes(report.size)} - ${trimmedPath}`
    );
  } catch (error) {
    toast(humanError(error));
    setStatus("Analysis failed.", "Static analysis error");
  } finally {
    state.busy = false;
    showOverlay(false);
    updateButtons();
  }
}

function renderAll() {
  renderLeft();
  renderCenter();
  renderChat();
  updateButtons();
}

function renderLeft() {
  const body = $("#left-body");
  clear(body);

  if (!state.report) {
    body.append(
      emptyState(
        "Workbench",
        "No binary loaded",
        "Open a PE, ELF or Mach-O sample to extract hashes, sections, imports, strings, call targets and x86/x64 disassembly.",
        "Ctrl + O"
      )
    );
    return;
  }

  if (state.leftTab === "sections") renderSections(body);
  else if (state.leftTab === "imports") renderImports(body);
  else if (state.leftTab === "strings") renderStrings(body);
  else if (state.leftTab === "findings") renderFindings(body);
  else renderOverview(body);
}

function metric(label, value) {
  const node = el("div", "metric");
  node.append(el("span", "metric-label", label));
  const text = el("span", "metric-value", value);
  text.title = String(value);
  node.append(text);
  return node;
}

function metaRow(label, value, copyable) {
  const row = el("div", "meta-row");
  row.append(el("span", "meta-key", label));
  const valueNode = el("span", "meta-value");
  const text = el("span", null, value);
  text.title = String(value);
  valueNode.append(text);
  if (copyable) {
    const button = el("button", "copy-btn", "copy");
    button.addEventListener("click", () => copyText(value, label));
    valueNode.append(button);
  }
  row.append(valueNode);
  return row;
}

function indicatorCard(indicator) {
  const card = el("div", `finding severity-${indicator.severity}`);
  card.append(el("div", "finding-title", indicator.title));
  card.append(el("div", "finding-detail", indicator.detail));
  return card;
}

function renderOverview(container) {
  const report = state.report;
  const grid = el("div", "metric-grid");
  grid.append(
    metric("Format", report.fileType),
    metric("Architecture", report.bits ? `${report.bits}-bit ${report.arch}` : report.arch),
    metric("Entropy", `${fmtEntropy(report.entropy)} / 8`),
    metric("Imports", report.importCount)
  );
  container.append(grid);

  const meta = el("div", "meta-list");
  meta.append(metaRow("Size", fmtBytes(report.size)));
  meta.append(
    metaRow("Entry", report.entry === null || report.entry === undefined ? "unknown" : fmtAddr(report.entry))
  );
  meta.append(metaRow("Image base", report.imageBase ? fmtAddr(report.imageBase) : "n/a"));
  meta.append(metaRow("Built", fmtTimestamp(report.timestamp)));
  meta.append(metaRow("SHA-256", report.sha256, true));
  meta.append(metaRow("MD5", report.md5, true));
  container.append(meta);

  container.append(el("p", "section-kicker", "Coverage"));
  const coverage = el("div", "meta-list");
  coverage.append(metaRow("Sections", report.sections.length));
  coverage.append(metaRow("Exports", report.exports.length));
  coverage.append(metaRow("Strings", `${report.strings.length} extracted / ${report.stringCount} found`));
  coverage.append(metaRow("Instructions", report.disassembly.length));
  coverage.append(metaRow("Call targets", report.callTargets.length));
  container.append(coverage);

  container.append(el("p", "section-kicker", "Top findings"));
  const cards = el("div", "summary-cards");
  if (!report.indicators.length) {
    const quiet = el("div", "finding severity-info");
    quiet.append(el("div", "finding-title", "No heuristic findings"));
    quiet.append(
      el(
        "div",
        "finding-detail",
        "Nothing matched the built-in indicators. Absence of findings is not proof of safety - inspect the entry point and imports manually."
      )
    );
    cards.append(quiet);
  } else {
    report.indicators.slice(0, 5).forEach((indicator) => cards.append(indicatorCard(indicator)));
  }
  container.append(cards);

  if (report.warnings.length) {
    container.append(el("p", "section-kicker", "Warnings"));
    const list = el("ul", "warning-list");
    report.warnings.forEach((warning) => list.append(el("li", null, warning)));
    container.append(list);
  }
}

function renderSections(container) {
  const report = state.report;
  container.append(el("p", "section-kicker", `${report.sections.length} sections`));
  if (!report.sections.length) {
    container.append(el("div", "disasm-note", "No section or segment table was recovered."));
    return;
  }

  report.sections.forEach((section) => {
    const row = el("div", "section-row");
    const top = el("div", "section-top");
    top.append(el("span", "section-name", section.name || "(unnamed)"));
    top.append(el("span", "mono muted", fmtAddr(section.address)));

    const sub = el("div", "section-sub");
    sub.append(el("span", null, `raw ${fmtBytes(section.rawSize)}`));
    sub.append(el("span", null, `vsize ${fmtBytes(section.virtualSize)}`));
    sub.append(el("span", null, `entropy ${fmtEntropy(section.entropy)}`));
    const permission = el(
      "span",
      `perm${section.executable ? " exec" : ""}${section.writable ? " write" : ""}`,
      section.perms
    );
    sub.append(permission);

    const bar = el("div", "entropy-bar");
    const fill = el("div", `entropy-fill${section.entropy > 7.2 ? " hot" : ""}`);
    fill.style.width = `${Math.max(2, Math.min(100, (Number(section.entropy) / 8) * 100))}%`;
    bar.append(fill);

    row.append(top, sub, bar);
    row.title = `file offset ${fmtAddr(section.offset)}`;
    container.append(row);
  });
}

function addLoadMore(container, remaining, onClick) {
  const button = el("button", "load-more", `Load more (${remaining.toLocaleString()} remaining)`);
  button.addEventListener("click", onClick);
  container.append(button);
}

function refocus(id) {
  const input = document.getElementById(id);
  if (!input) return;
  input.focus();
  const value = input.value;
  if (typeof input.setSelectionRange === "function") {
    input.setSelectionRange(value.length, value.length);
  }
}

function renderImports(container) {
  const report = state.report;
  const filterRow = el("div", "filter-row");
  const input = el("input");
  input.id = "import-filter";
  input.type = "search";
  input.placeholder = `Filter ${report.imports.length} imports`;
  input.value = state.importFilter;
  input.addEventListener("input", () => {
    state.importFilter = input.value;
    state.importLimit = 400;
    renderLeft();
    refocus("import-filter");
  });
  filterRow.append(input);
  container.append(filterRow);

  const needle = state.importFilter.trim().toLowerCase();
  const filtered = report.imports.filter(
    (item) =>
      !needle ||
      item.name.toLowerCase().includes(needle) ||
      item.library.toLowerCase().includes(needle)
  );

  container.append(
    el(
      "p",
      "section-kicker",
      `${filtered.length.toLocaleString()} shown of ${report.imports.length.toLocaleString()} imports`
    )
  );

  if (!filtered.length) {
    container.append(el("div", "disasm-note", "No imports match this filter."));
    return;
  }

  const list = el("div");
  filtered.slice(0, state.importLimit).forEach((item) => {
    const row = el("div", "import-row");
    row.append(el("span", "lib", item.library || "?"));
    row.append(el("span", "import-name", item.name));
    row.title = `${item.library}!${item.name} - double-click to copy`;
    row.addEventListener("dblclick", () => copyText(item.name, "Import"));
    list.append(row);
  });
  container.append(list);

  if (filtered.length > state.importLimit) {
    addLoadMore(container, filtered.length - state.importLimit, () => {
      state.importLimit += 400;
      renderLeft();
    });
  }
}

function renderStrings(container) {
  const report = state.report;
  const filterRow = el("div", "filter-row");
  const input = el("input");
  input.id = "string-filter";
  input.type = "search";
  input.placeholder = `Filter ${report.strings.length} strings`;
  input.value = state.stringFilter;
  input.addEventListener("input", () => {
    state.stringFilter = input.value;
    state.stringLimit = 300;
    renderLeft();
    refocus("string-filter");
  });
  filterRow.append(input);
  container.append(filterRow);

  const needle = state.stringFilter.trim().toLowerCase();
  const filtered = report.strings.filter(
    (item) => !needle || item.text.toLowerCase().includes(needle)
  );

  container.append(
    el(
      "p",
      "section-kicker",
      `${filtered.length.toLocaleString()} shown of ${report.strings.length.toLocaleString()} extracted (${report.stringCount.toLocaleString()} total)`
    )
  );

  if (!filtered.length) {
    container.append(el("div", "disasm-note", "No strings match this filter."));
    return;
  }

  const list = el("div");
  filtered.slice(0, state.stringLimit).forEach((item) => {
    const row = el("div", "string-row");
    const head = el("div", "string-head");
    head.append(el("span", "string-offset", fmtAddr(item.offset)));
    head.append(el("span", "string-kind", item.kind));
    row.append(head);
    row.append(el("div", "string-text", item.text));
    row.title = "Click to copy this string";
    row.addEventListener("click", () => copyText(item.text, "String"));
    list.append(row);
  });
  container.append(list);

  if (filtered.length > state.stringLimit) {
    addLoadMore(container, filtered.length - state.stringLimit, () => {
      state.stringLimit += 300;
      renderLeft();
    });
  }
}

function renderFindings(container) {
  const report = state.report;
  container.append(el("p", "section-kicker", `${report.indicators.length} heuristic findings`));

  if (!report.indicators.length) {
    container.append(
      el(
        "div",
        "disasm-note",
        "No heuristic findings. Treat this as a starting point, not a clean bill of health."
      )
    );
  } else {
    const cards = el("div", "summary-cards");
    report.indicators.forEach((indicator) => cards.append(indicatorCard(indicator)));
    container.append(cards);
  }

  if (report.warnings.length) {
    container.append(el("p", "section-kicker", "Warnings"));
    const list = el("ul", "warning-list");
    report.warnings.forEach((warning) => list.append(el("li", null, warning)));
    container.append(list);
  }

  if (report.exports.length) {
    container.append(el("p", "section-kicker", `Exports (${report.exports.length})`));
    const list = el("div");
    report.exports.slice(0, 200).forEach((name) => {
      const row = el("div", "import-row");
      row.append(el("span", "import-name", name));
      row.title = "Click to copy";
      row.addEventListener("click", () => copyText(name, "Export"));
      list.append(row);
    });
    container.append(list);
    if (report.exports.length > 200) {
      container.append(
        el("div", "disasm-note", `... ${report.exports.length - 200} more exports`)
      );
    }
  }
}

function renderCenter() {
  const body = $("#center-body");
  clear(body);

  document
    .querySelectorAll("#center-tabs button")
    .forEach((button) =>
      button.classList.toggle("active", button.dataset.ctab === state.centerTab)
    );
  $("#disasm-tools").classList.toggle("hidden", state.centerTab !== "disasm");

  if (!state.report) {
    body.append(
      emptyState(
        "Disassembly",
        "Nothing loaded yet",
        "Open a binary to decode its executable sections with iced-x86 and map direct call targets.",
        "PE - ELF - Mach-O - x86 / x64"
      )
    );
    return;
  }

  if (state.centerTab === "targets") renderTargets(body);
  else renderDisassembly(body);
}

function renderDisassembly(container) {
  const report = state.report;
  const needle = state.disasmSearch.trim().toLowerCase();

  const lines = report.disassembly.filter((line) => {
    if (state.disasmFilter === "calls" && line.kind !== "call") return false;
    if (state.disasmFilter === "branches" && line.kind !== "jmp" && line.kind !== "jcc") {
      return false;
    }
    if (
      state.disasmFilter === "control" &&
      line.kind !== "call" &&
      line.kind !== "jmp" &&
      line.kind !== "jcc"
    ) {
      return false;
    }
    if (needle) {
      const haystack = `${line.address.toString(16)} ${line.bytes} ${line.text}`.toLowerCase();
      if (!haystack.includes(needle)) return false;
    }
    return true;
  });

  if (!lines.length) {
    container.append(
      el(
        "div",
        "disasm-note",
        report.disassembly.length
          ? "No instructions match the current filter."
          : "No x86/x64 instructions were decoded for this sample."
      )
    );
    return;
  }

  const list = el("div", "disasm-list");
  lines.slice(0, 3000).forEach((line) => {
    const row = el("div", `disasm-row kind-${line.kind}`);
    const address = el("span", "disasm-addr", `0x${line.address.toString(16).padStart(8, "0")}`);
    address.title = "Click to copy this address";
    address.addEventListener("click", () =>
      copyText(`0x${line.address.toString(16)}`, "Address")
    );
    row.append(address);
    row.append(el("span", "disasm-bytes", line.bytes));
    row.append(el("span", "disasm-text", line.text));
    list.append(row);
  });
  container.append(list);

  if (lines.length > 3000) {
    container.append(
      el(
        "div",
        "disasm-note",
        `Showing the first 3,000 of ${lines.length.toLocaleString()} matching instructions.`
      )
    );
  }
}

function renderTargets(container) {
  const report = state.report;
  if (!report.callTargets.length) {
    container.append(
      el(
        "div",
        "disasm-note",
        "No direct call targets were recovered. The sample may use indirect calls, be packed, or contain no decodable code."
      )
    );
    return;
  }

  const table = el("table", "targets-table");
  const head = el("thead");
  const headRow = el("tr");
  ["Address", "Direct calls", "Est. instructions"].forEach((label) =>
    headRow.append(el("th", null, label))
  );
  head.append(headRow);
  table.append(head);

  const body = el("tbody");
  report.callTargets.forEach((target) => {
    const row = el("tr");
    const address = el("td", null, `0x${target.address.toString(16)}`);
    address.title = "Jump to this address in the disassembly";
    address.addEventListener("click", () => jumpToAddress(target.address));
    row.append(address);
    row.append(el("td", null, target.calls));
    row.append(el("td", null, target.instructions));
    body.append(row);
  });
  table.append(body);
  container.append(table);
  container.append(
    el(
      "div",
      "disasm-note",
      "Direct call destinations inside executable sections. Counts come from the decoded sample, not full control-flow recovery."
    )
  );
}

function jumpToAddress(address) {
  state.centerTab = "disasm";
  state.disasmFilter = "all";
  state.disasmSearch = address.toString(16);
  $("#disasm-search").value = state.disasmSearch;
  $("#disasm-filter").value = "all";
  renderCenter();
}

function chatNote(text) {
  return el("div", "chat-note", text);
}

function renderMessage(message) {
  const wrapper = el("div", `chat-msg ${message.role}`);
  wrapper.append(el("div", "msg-label", message.role === "user" ? "You" : "ReaCopilot"));
  wrapper.append(el("div", "msg-body", message.content));
  const actions = el("div", "msg-actions");
  const copy = el("button", null, "copy");
  copy.addEventListener("click", () => copyText(message.content, "Message"));
  actions.append(copy);
  wrapper.append(actions);
  return wrapper;
}

function typingIndicator() {
  const node = el("div", "typing");
  node.append(el("i"), el("i"), el("i"), el("span", null, "thinking"));
  return node;
}

function renderChat() {
  const log = $("#chat-log");
  clear(log);

  if (!state.messages.length) {
    log.append(
      chatNote(
        state.report
          ? `Loaded ${state.report.name}. Ask about the entry point, imports, notable strings, call targets, or where to dig next.`
          : "Open a binary to load analysis context. The copilot only sees the extracted report - it never executes the sample."
      )
    );
  } else {
    state.messages.forEach((message) => log.append(renderMessage(message)));
  }

  if (state.typing) log.append(typingIndicator());
  log.scrollTop = log.scrollHeight;
}

async function sendMessage(text) {
  const content = String(text === undefined ? $("#chat-input").value : text).trim();
  if (!content) return;
  if (!state.report) {
    toast("Open and analyze a binary first.");
    return;
  }
  if (!invoke) {
    toast("AI chat requires the Tauri app.");
    return;
  }
  if (!state.settings || !state.settings.model) {
    toast("Configure an AI provider first.");
    openSettings();
    return;
  }
  if (state.busy) return;

  state.busy = true;
  state.typing = true;
  state.messages.push({ role: "user", content });
  $("#chat-input").value = "";
  renderChat();
  updateButtons();

  try {
    const reply = await invoke("ai_chat", {
      messages: state.messages,
      context: state.context,
    });
    state.messages.push({ role: "assistant", content: String(reply) });
  } catch (error) {
    state.messages.push({
      role: "assistant",
      content: `Request failed: ${humanError(error)}`,
    });
  } finally {
    state.busy = false;
    state.typing = false;
    renderChat();
    updateButtons();
  }
}

function analyzeWithAi() {
  if (!state.report) return;
  if (!state.settings || !state.settings.model) {
    toast("Add an AI provider to use the copilot.");
    openSettings();
    return;
  }
  sendMessage(
    "Triage this binary: what is it, what is the most likely purpose, and which evidence is strongest? Then rank the next places to investigate."
  );
}

function mdCell(value) {
  return String(value === null || value === undefined ? "" : value).replace(/\|/g, "\\|");
}

function buildMarkdown() {
  const report = state.report;
  const lines = [];
  const push = (...items) => lines.push(...items);

  push(`# ReaCopilot report - ${report.name}`, "");
  push(`Generated: ${new Date().toLocaleString()}`, "");
  push("## Identity", "", "| Field | Value |", "| --- | --- |");
  [
    ["Path", report.path],
    ["Size", `${report.size} bytes (${fmtBytes(report.size)})`],
    ["Format", report.fileType],
    ["Architecture", `${report.bits ? `${report.bits}-bit ` : ""}${report.arch} (${report.endian})`],
    ["Entry point", report.entry === null ? "unknown" : fmtAddr(report.entry)],
    ["Image base", report.imageBase ? fmtAddr(report.imageBase) : "n/a"],
    ["Build timestamp", fmtTimestamp(report.timestamp)],
    ["Entropy", `${fmtEntropy(report.entropy)} / 8`],
    ["SHA-256", report.sha256],
    ["MD5", report.md5],
  ].forEach(([key, value]) => push(`| ${mdCell(key)} | ${mdCell(value)} |`));

  push("", "## Findings", "");
  if (!report.indicators.length) {
    push("No heuristic findings.", "");
  } else {
    report.indicators.forEach((indicator) =>
      push(`- **${indicator.severity.toUpperCase()} - ${indicator.title}**: ${indicator.detail}`)
    );
    push("");
  }

  push("## Warnings", "");
  if (!report.warnings.length) push("None.", "");
  else {
    report.warnings.forEach((warning) => push(`- ${warning}`));
    push("");
  }

  push("## Sections", "", "| Name | Address | VSize | Raw | Entropy | Perms |", "| --- | --- | --- | --- | --- | --- |");
  report.sections.forEach((section) =>
    push(
      `| ${mdCell(section.name || "(unnamed)")} | ${fmtAddr(section.address)} | ${section.virtualSize} | ${section.rawSize} | ${fmtEntropy(section.entropy)} | ${section.perms} |`
    )
  );

  push("", `## Imports (${report.importCount})`, "");
  report.imports.slice(0, 200).forEach((item) =>
    push(`- \`${item.library}!${item.name}\``)
  );
  if (report.imports.length > 200) {
    push(`- ... ${report.imports.length - 200} more imports omitted`);
  }

  push("", `## Strings (first 300 of ${report.strings.length})`, "");
  report.strings.slice(0, 300).forEach((item) =>
    push(`- \`0x${item.offset.toString(16)}\` [${item.kind}] ${item.text}`)
  );

  push("", "## Call targets (top 100)", "");
  report.callTargets.slice(0, 100).forEach((target) =>
    push(
      `- ${fmtAddr(target.address)}: ${target.calls} direct calls, ~${target.instructions} instructions`
    )
  );

  push("", "## Disassembly sample (first 400 lines)", "", "```asm");
  report.disassembly.slice(0, 400).forEach((line) =>
    push(
      `${line.address.toString(16).padStart(16, "0")}  ${line.bytes.padEnd(24)}  ${line.text}`
    )
  );
  push("```", "");

  if (state.messages.length) {
    push("## AI conversation", "");
    state.messages.forEach((message) => {
      push(`### ${message.role === "user" ? "Analyst" : "ReaCopilot"}`, "", message.content, "");
    });
  }

  push("---", "", "Generated by ReaCopilot. Static heuristics are indicators, not proof.");
  return lines.join("\n");
}

async function exportReport() {
  if (!state.report) return;
  if (!requireNative()) return;
  try {
    const content = buildMarkdown();
    const path = await invoke("export_report", {
      defaultName: `${state.report.name}.reacopilot.md`,
      content,
    });
    if (path) toast(`Report exported to ${path}`);
  } catch (error) {
    toast(humanError(error));
  }
}

function wire() {
  $("#open-btn").addEventListener("click", openBinary);
  $("#ai-analyze-btn").addEventListener("click", analyzeWithAi);
  $("#export-btn").addEventListener("click", exportReport);
  $("#settings-btn").addEventListener("click", openSettings);
  $("#send-btn").addEventListener("click", () => sendMessage());
  $("#clear-chat").addEventListener("click", () => {
    state.messages = [];
    renderChat();
  });

  $("#left-tabs").addEventListener("click", (event) => {
    const tab = event.target.closest(".tab");
    if (!tab) return;
    state.leftTab = tab.dataset.tab;
    document
      .querySelectorAll("#left-tabs .tab")
      .forEach((item) => item.classList.toggle("active", item === tab));
    renderLeft();
  });

  $("#center-tabs").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-ctab]");
    if (!button) return;
    state.centerTab = button.dataset.ctab;
    renderCenter();
  });

  $("#disasm-filter").addEventListener("change", (event) => {
    state.disasmFilter = event.target.value;
    renderCenter();
  });

  $("#disasm-search").addEventListener("input", (event) => {
    state.disasmSearch = event.target.value;
    renderCenter();
  });

  $("#quick-prompts").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-prompt]");
    if (!button) return;
    sendMessage(button.dataset.prompt);
  });

  $("#chat-input").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      sendMessage();
    }
  });

  $("#settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!requireNative()) return;
    const tokens = Number($("#set-tokens").value);
    const temperature = Number($("#set-temp").value);
    const settings = {
      baseUrl: $("#set-base").value.trim(),
      model: $("#set-model").value.trim(),
      apiKey: $("#set-key").value.trim(),
      maxTokens: Number.isFinite(tokens) && tokens > 0 ? tokens : 2048,
      temperature: Number.isFinite(temperature) ? temperature : 0.2,
    };
    try {
      await invoke("save_settings", { settings });
      state.settings = settings;
      applySettings();
      $("#settings-dialog").close();
      toast("AI provider saved.");
    } catch (error) {
      toast(humanError(error));
    }
  });

  $("#cancel-settings").addEventListener("click", () => $("#settings-dialog").close());

  document.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "o") {
      event.preventDefault();
      openBinary();
    }
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
      const target = event.target;
      if (target && target.id === "chat-input") return;
      event.preventDefault();
      if (state.report) sendMessage();
    }
  });
}

async function bootstrap() {
  wire();
  renderAll();

  if (!invoke) {
    setStatus(
      "Browser preview - launch the Tauri app with `cargo run` to analyze files.",
      "No native bridge detected"
    );
    return;
  }

  try {
    state.settings = await invoke("get_settings");
    applySettings();
    applySettingsToForm();
  } catch (error) {
    console.error(error);
    toast("Could not load AI settings.");
  }

  setStatus("Ready - open a binary to begin.", "Local static analysis - files stay on this machine");
}

document.addEventListener("DOMContentLoaded", bootstrap);
