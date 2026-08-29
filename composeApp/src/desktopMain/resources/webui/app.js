/* ═══════════════════════════════════════════════════════════════
   IPTV Family · Web remota — app.js
   Auth → estado → SSE → reproductor HLS local → lista por lotes
   ═══════════════════════════════════════════════════════════════ */
"use strict";

/* ─── Utilidades ─── */
const $ = (id) => document.getElementById(id);
const escapeHtml = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => (
  { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
));
const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

const state = {
  token: null,
  role: "viewer",       // "admin" = manda; "viewer" = solo mira lo que pone el admin
  channels: [],
  groupNames: new Map(),// id de grupo → nombre legible (Xtream usa ids numéricos)
  favIds: new Set(),
  now: {},
  group: null,          // categoría seleccionada (null = todas)
  groups: [],           // [{id, name, count, kind}] tal cual llega del servidor
  kind: null,           // "live" | "vod" | "series"; null = todo
  catOpen: false,       // panel de categorías desplegado
  catQuery: "",         // filtro dentro del panel de categorías
  onlyFavs: false,
  query: "",
  visible: 0,           // nº de tarjetas renderizadas
  BATCH: 120,
  hls: null,
  streamUrl: null,      // URL cargada actualmente en <video>
  streamChannelId: null,
  streamLoading: false,
  localError: null,      // error del lado del navegador (distinto del del escritorio)
  retryTimer: null,
  retryCount: 0,
  pendingPlay: false,   // play() bloqueado → mostrar big-play
  audioCheckTimer: null,
  audioPicked: false,   // ya se eligió pista de audio para el canal en curso
  iosFullscreen: false, // iOS no expone document.fullscreenElement: hay que anotarlo
  searchText: new Map(),// id de canal → texto de busqueda ya normalizado
  tab: "ver",           // pestaña de movil: "ver" | "canales"
  sse: null,
  started: false,
};

const isAdmin = () => state.role === "admin";

/**
 * La sesion va en una cookie HttpOnly que planta /login, asi que las peticiones
 * normales no necesitan cabecera: basta `credentials: "include"`. El token solo
 * se usa aparte para SSE y el <video>, que no pueden mandar cabeceras ni,
 * segun el navegador, cookies en peticiones de subrecursos.
 */
async function api(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: { "Content-Type": "application/json", ...(opts.headers || {}) },
    credentials: "include",
  });
  if (res.status === 401) { showLogin("La sesión ha caducado. Vuelve a entrar."); throw new Error("unauthorized"); }
  if (res.status === 403) { throw new Error("forbidden"); }
  return res;
}
async function apiPost(path, body) {
  const res = await api(path, { method: "POST", body: body != null ? JSON.stringify(body) : undefined });
  if (!res.ok) throw new Error("HTTP " + res.status);
  try { return await res.json(); } catch { return null; }
}

/* ─── Login / auth ─── */
function showLogin(message) {
  stopEverything();
  $("app-view").hidden = true;
  $("login-view").hidden = false;
  const err = $("login-error");
  if (message) { err.textContent = message; err.hidden = false; }
  else { err.hidden = true; }
  setTimeout(() => $("user-input").focus(), 50);
}

/**
 * Primer arranque: no hay ninguna cuenta todavia, asi que el formulario crea la
 * de administrador en vez de iniciar sesion. Evita el huevo y la gallina de
 * tener que ir al PC a crear la primera cuenta para poder entrar.
 */
function setSetupMode(on) {
  state.setupMode = on;
  $("login-submit").textContent = on ? "Crear cuenta de administrador" : "Entrar";
  $("login-hint").innerHTML = on
    ? "Es la primera vez: crea la cuenta de <strong>administrador</strong>. Podrás añadir más cuentas después."
    : "Las cuentas se crean en la app de escritorio, en <strong>Ajustes → Servidor web</strong>, o desde el botón de usuarios.";
  $("pass-input").setAttribute("autocomplete", on ? "new-password" : "current-password");
}

/** Intenta entrar con la sesion que ya tenga el navegador (cookie). */
async function tryResumeSession() {
  const res = await fetch("/api/state", { credentials: "include" });
  if (!res.ok) return false;
  const data = await res.json();
  enterApp(data);
  return true;
}

async function doLogin(username, password) {
  const res = await fetch("/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
    credentials: "include",
  });
  if (!res.ok) {
    showLogin("Usuario o contraseña incorrectos.");
    return false;
  }
  const info = await res.json();
  // Clave para SSE y <video>, que no pueden mandar cabeceras propias.
  state.token = info.streamKey || null;
  return tryResumeSession();
}

/** Crea la primera cuenta de administrador (solo posible si no hay ninguna). */
async function doSetup(username, password) {
  const res = await fetch("/api/setup", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
    credentials: "include",
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    showLogin(setupErrorText(err.error));
    return false;
  }
  return doLogin(username, password);
}

function setupErrorText(code) {
  switch (code) {
    case "username_too_short": return "El usuario debe tener al menos 3 caracteres.";
    case "weak_password": return "La contraseña debe tener al menos 6 caracteres.";
    case "username_required": return "Escribe un nombre de usuario.";
    case "password_required": return "Escribe una contraseña.";
    case "already_configured": return "Ya hay cuentas creadas: inicia sesión.";
    case "username_taken": return "Ya existe una cuenta con ese nombre.";
    default: return "No se pudo crear la cuenta.";
  }
}

function enterApp(data) {
  // Los controles se cablean una sola vez al arrancar (ver boot), no aqui: si se
  // hiciera en cada entrada, cerrar y volver a abrir sesion duplicaria los
  // listeners y cada clic contaria dos veces.
  $("login-view").hidden = true;
  $("app-view").hidden = false;
  connectEvents(); // reconecta siempre (showLogin cierra el SSE anterior)
  applyState(data);
}

/* ─── Estado / SSE ─── */
function applyState(data) {
  state.role = data.role === "admin" ? "admin" : "viewer";
  state.username = data.username || null;
  state.channels = Array.isArray(data.channels) ? data.channels : [];
  state.groups = Array.isArray(data.groups) ? data.groups : [];
  state.groupNames = new Map(state.groups.map((g) => [g.id, g.name]));
  state.favIds = new Set(data.favoriteChannelIds || []);
  state.now = data.nowPlaying || {};
  $("playlist-name").textContent = data.playlistName || "Sin lista";
  applyRole();
  buildSearchIndex();
  renderKindChips();
  renderCategories();
  resetList();
  applyNowPlaying(state.now);
}

/**
 * Un invitado no manda: se le esconde la lista de canales y todos los mandos
 * salvo el volumen y la pantalla completa (que son locales de su navegador y no
 * afectan a lo que se ve en el salon). El servidor tambien lo bloquea por su
 * cuenta con 403; esto es solo para no ensenar botones que no van a funcionar.
 */
function applyRole() {
  const admin = isAdmin();
  document.body.classList.toggle("is-viewer", !admin);
  ["btn-prev", "btn-next", "btn-stop", "btn-playpause"].forEach((id) => {
    const el = $(id);
    if (el) { el.hidden = !admin; el.disabled = !admin; }
  });
  // Solo el administrador gestiona cuentas.
  $("btn-users").hidden = !admin;

  const badge = $("role-badge");
  if (badge) {
    const who = state.username ? `${state.username} · ` : "";
    badge.hidden = false;
    badge.textContent = admin ? `${who}administrador` : `${who}solo lectura`;
  }
}

function handleChannelsChanged() {
  api("/api/state").then((r) => r.json()).then(applyState).catch(() => {});
}

function connectEvents() {
  try { state.sse?.close(); } catch {}
  const es = new EventSource(
    state.token ? "/api/events?s=" + encodeURIComponent(state.token) : "/api/events"
  );
  state.sse = es;
  es.onopen = () => setOnline(true);
  es.addEventListener("now-playing", (e) => {
    setOnline(true);
    try { applyNowPlaying(JSON.parse(e.data)); } catch {}
  });
  es.addEventListener("channels", handleChannelsChanged);
  es.onerror = () => setOnline(false);
}
function setOnline(ok) {
  const c = $("connection");
  c.classList.toggle("is-online", ok);
  c.classList.toggle("is-offline", !ok);
  $("connection-label").textContent = ok ? "En línea" : "Desconectado";
}

/* ─── Tema ─── */
function setTheme(t) {
  document.documentElement.dataset.theme = t;
  localStorage.setItem("web_theme", t);
  document.querySelectorAll(".theme-opt").forEach((b) =>
    b.setAttribute("aria-checked", String(b.dataset.themeOpt === t)));
}

/**
 * Unico sitio que decide que capa se ve encima del video (nada / cargando /
 * error). Antes la visibilidad se tocaba desde dos sitios independientes -- los
 * eventos del <video> local y el estado que llega del escritorio por SSE -- y la
 * rama del escritorio solo sabia MOSTRAR el circulo de "Sintonizando", nunca
 * ocultarlo: si el escritorio volvia a bufferar despues de que el video local ya
 * estuviera en marcha, el circulo se quedaba clavado encima de una imagen que se
 * veia perfectamente.
 *
 * La regla es: manda lo que pasa en ESTE navegador. Si aqui hay imagen
 * avanzando, no hay nada que esperar, diga lo que diga el escritorio.
 */
function renderOverlays() {
  const video = $("video");
  const np = state.now || {};

  // El <video> va de verdad: no esta en pausa, tiene datos suficientes para
  // pintar el fotograma actual y no se ha terminado.
  const playingLocally = Boolean(
    state.streamUrl && !video.paused && !video.ended && video.readyState >= 3
  );

  // Un error (del escritorio: canal caido; o local: el stream no carga) sigue
  // mereciendo aviso, pero no si aqui se esta viendo la imagen igualmente.
  const errorMsg = state.localError || np.error;
  const showError = Boolean(errorMsg) && !playingLocally;
  if (showError) $("error-msg").textContent = errorMsg;

  // "Cargando": o el navegador esta esperando datos, o el escritorio todavia
  // esta sintonizando y aqui aun no hay imagen.
  const waiting = !playingLocally && !showError && Boolean(
    state.streamUrl && (state.streamLoading || np.isBuffering)
  );

  const idle = !state.streamUrl && !np.channelId && !showError;

  $("overlay-error").hidden = !showError;
  $("overlay-buffer").hidden = !waiting;
  $("overlay-idle").hidden = !idle;
  if (playingLocally) $("big-play").hidden = true;
}

/* ─── Now playing → UI ─── */
function applyNowPlaying(np) {
  const prevChannel = state.now.channelId;
  state.now = np || {};

  const hasChannel = Boolean(np?.channelId);
  $("np-meta").hidden = !hasChannel;
  if (hasChannel) {
    $("np-name").textContent = np.channelNumber != null
      ? `${np.channelNumber} · ${np.channelName || ""}`
      : (np.channelName || "");
    $("np-group").textContent = np.group || "";
    const epgLine = $("np-epg");
    epgLine.textContent = [np.epgNow && `Ahora: ${np.epgNow}`, np.epgNext && `Luego: ${np.epgNext}`]
      .filter(Boolean).join(" · ");
    const logo = $("np-logo");
    if (np.logoUrl) { logo.src = np.logoUrl; logo.hidden = false; logo.onerror = () => { logo.hidden = true; }; }
    else logo.hidden = true;
  }
  $("live-badge").hidden = !(np?.isPlaying && !np?.error);

  if (np?.error) $("error-msg").textContent = np.error;
  renderOverlays();

  // sincronizar play/pausa del botón
  setPlayIcon(Boolean(np?.isPlaying));

  // volumen / mute remotos → UI
  if (!volumeDrag) {
    $("volume").value = np?.volume ?? 80;
    updateVolumeFill();
  }
  setMuteIcon(Boolean(np?.isMuted));

  // Cambio de canal (lo haya pedido esta pestaña, otro navegador o el propio
  // escritorio): recargar el stream. Antes esto exigia `np.isPlaying`, que obliga
  // a esperar a que VLC termine de bufferar, y ademas `!state.streamLoading`, una
  // bandera que se ponia a true y nunca volvia a false: tras el primer canal la
  // condicion no se cumplia nunca y la web se quedaba con el stream viejo.
  if (hasChannel && np.channelId !== state.streamChannelId) {
    startStream();
  }
  if (!hasChannel && state.streamUrl) {
    // El escritorio ha parado: soltar el <video> en vez de dejar el ultimo
    // fotograma congelado como si siguiera emitiendo.
    teardownLocalVideo();
    renderOverlays();
  }
  if (prevChannel !== np?.channelId) state.retryCount = 0;
}

function setPlayIcon(playing) {
  document.querySelector("#btn-playpause .ic-play").hidden = playing;
  document.querySelector("#btn-playpause .ic-pause").hidden = !playing;
}
function setMuteIcon(muted) {
  const b = $("btn-mute");
  b.setAttribute("aria-pressed", String(muted));
  b.classList.toggle("is-active", muted);
  document.querySelector("#btn-mute .ic-vol-on").hidden = muted;
  document.querySelector("#btn-mute .ic-vol-off").hidden = !muted;
}
/* ─── Reproductor HLS (hls.min.js servido localmente) ─── */
let volumeDrag = false;

/**
 * El `ch` no lo usa el servidor para elegir el canal (siempre sirve el que suena
 * en el escritorio): esta para que la URL cambie al cambiar de canal. Con una URL
 * fija, hls.js veia el MISMO recurso devolver de golpe una playlist totalmente
 * distinta, lo interpretaba como un salto del directo y tardaba muchisimo en
 * resincronizar — era la causa de "cuando cambio de canal tarda mucho en
 * aparecer la imagen".
 */
function streamUrl() {
  const ch = state.now.channelId || "none";
  const key = state.token ? "&s=" + encodeURIComponent(state.token) : "";
  return "/stream/current.m3u8?ch=" + encodeURIComponent(ch) + key;
}

function destroyHls() {
  if (state.hls) { try { state.hls.destroy(); } catch {} state.hls = null; }
  clearTimeout(state.retryTimer);
  clearTimeout(state.audioCheckTimer);
  hideAudioNotice();
}

function startStream() {
  const video = $("video");
  const url = streamUrl();
  state.streamLoading = true;
  state.streamChannelId = state.now.channelId || null;
  state.streamUrl = url;
  state.localError = null; // el error del canal anterior no aplica al nuevo
  // Las pistas del canal anterior no valen para el nuevo: hay que volver a
  // elegir, o el nuevo canal se quedaria con lo que decidiera el HLS.
  state.audioPicked = false;
  $("big-play").hidden = true;
  destroyHls();
  renderOverlays();

  // Si en unos segundos no llega ningun fotograma, el canal no esta emitiendo
  // (o el panel no entrega segmentos). Mejor avisar que quedarse en el loop de
  // "Sintonizando..." hasta el infinito.
  let gotMedia = false;
  let streamTimeout = setTimeout(() => {
    if (!gotMedia) {
      showStreamError("El canal no está emitiendo datos en este momento. Prueba otro canal.",
        false);
    }
  }, 12000);

  if (window.Hls && Hls.isSupported()) {
    const hls = new Hls({
      backBufferLength: 30,
      manifestLoadingMaxRetry: 2,
      levelLoadingMaxRetry: 2,
      fragLoadingMaxRetry: 4,
    });
    state.hls = hls;
    // Senal real de que hay fotogramas: un <video> que emite dispara timeupdate
    // pronto (el manifiesto puede parsear aunque los segmentos fallen).
    const markMedia = () => {
      gotMedia = true;
      clearTimeout(streamTimeout);
      video.removeEventListener("timeupdate", markMedia);
      video.removeEventListener("playing", markMedia);
    };
    video.addEventListener("timeupdate", markMedia);
    video.addEventListener("playing", markMedia);
    hls.on(Hls.Events.ERROR, (_evt, data) => {
      if (!data.fatal) return;
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR && state.retryCount < 4) {
        state.retryCount++;
        hls.startLoad();
      } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR && state.retryCount < 4) {
        state.retryCount++;
        hls.recoverMediaError();
      } else if (!document.hidden) {
        showStreamError("No se pudo cargar el stream. Comprueba que el canal emite en HLS.");
        scheduleRetry();
      }
    });
    // Sin esto el navegador se queda con la pista que el HLS marque por defecto,
    // que en muchos canales no es la española (se oía francés en canales que en
    // la app de escritorio suenan en español).
    hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => autoPickAudio());
    hls.on(Hls.Events.AUDIO_TRACK_SWITCHED, () => renderAudioPicker());
    hls.loadSource(url);
    hls.attachMedia(video);
  } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
    video.src = url; // Safari: HLS nativo
    // En HLS nativo las pistas aparecen en video.audioTracks, no en hls.js.
    if (video.audioTracks) {
      video.audioTracks.addEventListener("addtrack", () => autoPickAudio());
      video.audioTracks.addEventListener("change", () => renderAudioPicker());
    }
  } else {
    showStreamError("Tu navegador no soporta reproducción HLS.");
    return;
  }

  // Volumen local coherente con el slider (el <video> arranca a 1.0 y el slider
  // mostraba otra cosa: parecia que el control de volumen no hacia nada).
  video.muted = false;
  video.volume = (Number($("volume").value) || 80) / 100;

  video.play().then(() => {
    state.pendingPlay = false;
    $("big-play").hidden = true;
    scheduleAudioCheck();
  }).catch(() => {
    // Autoplay con sonido bloqueado (politica del navegador). En vez de dejar la
    // pantalla en negro esperando un clic, arrancamos SIN sonido -- que si esta
    // permitido -- y ofrecemos un boton para activarlo con un gesto del usuario.
    video.muted = true;
    video.play().then(() => {
      state.pendingPlay = false;
      $("big-play").hidden = true;
      showAudioNotice("Toca para activar el sonido", true);
      scheduleAudioCheck();
    }).catch(() => {
      state.pendingPlay = true;
      $("big-play").hidden = false;
    });
  });
}

/* ─── Aviso de audio (sin sonido por bloqueo o por códec no soportado) ─── */
function showAudioNotice(text, clickToUnmute) {
  const el = $("audio-notice");
  if (!el) return;
  el.textContent = text;
  el.hidden = false;
  el.classList.toggle("is-clickable", Boolean(clickToUnmute));
  el.dataset.action = clickToUnmute ? "unmute" : "";
}
function hideAudioNotice() {
  const el = $("audio-notice");
  if (el) el.hidden = true;
}

/**
 * Muchos canales IPTV emiten el audio en AC-3/E-AC-3 o MP2, códecs que los
 * navegadores NO pueden decodificar (VLC en el escritorio sí, de ahí que se oiga
 * en la app y no en la web). No hay arreglo posible desde el navegador: lo único
 * honesto es detectarlo y decirlo, en vez de dejar al usuario pensando que hay
 * un fallo de volumen. Se comprueba que haya bytes de audio decodificados unos
 * segundos después de que empiece la imagen.
 */
function scheduleAudioCheck() {
  clearTimeout(state.audioCheckTimer);
  state.audioCheckTimer = setTimeout(() => {
    const video = $("video");
    if (video.paused || !state.streamUrl) return;
    if (video.muted) return; // el aviso de "toca para activar" ya está puesto

    const decoded = video.webkitAudioDecodedByteCount;
    const hasAudio = video.mozHasAudio
      ?? (video.audioTracks ? video.audioTracks.length > 0 : undefined);
    const hlsAudio = state.hls?.audioTracks?.length;

    // Solo avisamos cuando la señal es clara: el navegador dice explícitamente
    // que no hay pista de audio, o lleva reproduciendo y no ha decodificado ni
    // un byte. Si el navegador no expone estos datos, no inventamos nada.
    const noTrack = hasAudio === false || (hlsAudio === 0 && hasAudio !== true);
    const nothingDecoded = typeof decoded === "number" && decoded === 0 && video.currentTime > 3;
    if (noTrack || nothingDecoded) {
      showAudioNotice("Este canal emite el audio en un formato que el navegador no puede reproducir (AC-3/MP2). En la app de escritorio sí se oye.", false);
    } else {
      hideAudioNotice();
    }
  }, 6000);
}

function scheduleRetry() {
  clearTimeout(state.retryTimer);
  const delay = Math.min(15000, 1500 * Math.pow(2, state.retryCount));
  state.retryTimer = setTimeout(() => {
    if (state.now.channelId && state.streamUrl) startStream();
  }, delay);
}

function showStreamError(msg) {
  state.streamLoading = false;
  // Se guarda en el estado (y no se pinta a mano) para que renderOverlays sea el
  // unico que decide: si el video local acaba arrancando igualmente, el aviso se
  // retira solo en vez de quedarse encima de una imagen que si se ve.
  state.localError = msg;
  $("error-msg").textContent = msg;
  renderOverlays();
}

/* ─── Pista de audio: español por defecto ──────────────────────────────────
   OJO: estas reglas son un espejo de AudioTrackPreference.kt (que es quien
   manda en la app de escritorio y en el transcodificador). Si cambian alli,
   hay que cambiarlas aqui: son dos runtimes distintos y no se puede compartir
   el codigo. */
const AUDIO_ES = ["es", "spa", "esp", "spanish", "espanol", "español", "cas", "cast",
  "castellano", "es-es", "spa-es", "lat", "es-419"];
const AUDIO_DESC = ["qad", "audiodesc", "audio desc", "descripc", "visually impaired",
  "comentario", "commentary", "narrat"];
const AUDIO_VOS = ["vos", "original", " ov", "subtitul"];

/** Quita acentos para que "Español" y "Espanol" puntuen igual. */
const fold = (s) => (s || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim();

/** Mismo baremo que AudioTrackPreference.score: mas alto = mejor. */
function audioScore(lang, name) {
  const l = fold(lang);
  const text = (fold(name) + " " + l).trim();
  // La audiodescripcion (el narrador que describe la imagen) va la PRIMERA en
  // muchos canales españoles, con el codigo "qad". Nunca es lo que se quiere.
  if (AUDIO_DESC.some((d) => text.includes(d))) return -100;
  let points = 0;
  if (AUDIO_ES.includes(l) || AUDIO_ES.some((e) => e.length > 3 && text.includes(fold(e)))) points += 100;
  if (AUDIO_VOS.some((v) => text.includes(v))) points -= 30;
  if (points === 0 && (l === "" || l === "und" || l === "mul")) points += 20;
  return points;
}

/** Las pistas del reproductor en curso, normalizadas: [{index, label, active}]. */
function audioTracksOf() {
  const hls = state.hls;
  if (hls && Array.isArray(hls.audioTracks) && hls.audioTracks.length) {
    return hls.audioTracks.map((t, i) => ({
      index: i,
      lang: t.lang || "",
      name: t.name || t.label || "",
      active: i === hls.audioTrack,
    }));
  }
  const native = $("video").audioTracks;
  if (native && native.length) {
    return Array.from(native).map((t, i) => ({
      index: i,
      lang: t.language || "",
      name: t.label || "",
      active: t.enabled,
    }));
  }
  return [];
}

function setAudioTrack(index) {
  const hls = state.hls;
  if (hls && Array.isArray(hls.audioTracks) && hls.audioTracks.length) {
    hls.audioTrack = index;
  } else {
    const native = $("video").audioTracks;
    if (native) for (let i = 0; i < native.length; i++) native[i].enabled = (i === index);
  }
  state.audioPicked = true;
  renderAudioPicker();
}

/** Etiqueta legible; si no se reconoce el idioma se enseña lo que venga. */
function audioLabel(t) {
  const text = (fold(t.name) + " " + fold(t.lang)).trim();
  if (AUDIO_DESC.some((d) => text.includes(d))) return "Audiodescripción";
  if (audioScore(t.lang, t.name) >= 100) return "Español";
  const NAMES = { en: "Inglés", eng: "Inglés", fr: "Francés", fra: "Francés", fre: "Francés",
    pt: "Portugués", por: "Portugués", it: "Italiano", ita: "Italiano",
    de: "Alemán", deu: "Alemán", ger: "Alemán", ca: "Catalán", cat: "Catalán",
    gl: "Gallego", glg: "Gallego", eu: "Euskera", eus: "Euskera", baq: "Euskera" };
  return NAMES[fold(t.lang)] || t.name || t.lang || `Pista ${t.index + 1}`;
}

/**
 * Elige la mejor pista al empezar un canal. Solo actua una vez por canal, para
 * no pisar al usuario si la cambia a mano.
 */
function autoPickAudio() {
  const tracks = audioTracksOf();
  renderAudioPicker();
  if (state.audioPicked || tracks.length < 2) return;
  let best = tracks[0], bestScore = audioScore(tracks[0].lang, tracks[0].name);
  for (const t of tracks.slice(1)) {
    const s = audioScore(t.lang, t.name);
    if (s > bestScore) { best = t; bestScore = s; }
  }
  const current = tracks.find((t) => t.active) || tracks[0];
  state.audioPicked = true;
  if (best.index !== current.index && bestScore > audioScore(current.lang, current.name)) {
    setAudioTrack(best.index);
  } else {
    renderAudioPicker();
  }
}

/** Boton "Audio" en los mandos: solo si el canal trae mas de una pista. */
function renderAudioPicker() {
  const wrap = $("audio-picker");
  if (!wrap) return;
  const tracks = audioTracksOf();
  wrap.hidden = tracks.length < 2;
  if (tracks.length < 2) { wrap.classList.remove("is-open"); return; }
  $("audio-menu").innerHTML = tracks.map((t) =>
    `<button type="button" class="audio-opt${t.active ? " is-active" : ""}" data-track="${t.index}">`
    + `${escapeHtml(audioLabel(t))}</button>`).join("");
}

/* ─── Pantalla completa ────────────────────────────────────────────────────
   En el iPhone NO existe la API de pantalla completa para elementos: Safari
   solo la ofrece sobre el propio <video>, y con nombre propio
   (`webkitEnterFullscreen`). El codigo llamaba a `requestFullscreen()` a secas,
   que alli no existe, asi que el boton no hacia absolutamente nada. */

const fsElement = () => document.fullscreenElement || document.webkitFullscreenElement || null;
const isFullscreen = () => Boolean(fsElement()) || state.iosFullscreen === true;

function toggleFullscreen() {
  const card = $("video-card");
  const video = $("video");

  // Se sale por donde se entro. Si estamos en el reproductor nativo de iOS hay
  // que cerrarlo por el <video>: `document.exitFullscreen` no lo saca de ahi
  // (y en el iPhone ni siquiera existe).
  if (state.iosFullscreen) {
    if (video.webkitExitFullscreen) video.webkitExitFullscreen();
    state.iosFullscreen = false;
    unlockOrientation();
    syncFullscreenUi();
    return;
  }
  if (fsElement()) {
    if (document.exitFullscreen) document.exitFullscreen().catch(() => {});
    else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
    unlockOrientation();
    return;
  }

  const request = card.requestFullscreen || card.webkitRequestFullscreen;
  if (request) {
    Promise.resolve(request.call(card)).then(lockLandscape).catch(() => {});
    return;
  }
  // iPhone: la unica via es el reproductor nativo sobre el <video>. Solo
  // funciona si el video ya tiene datos cargados.
  if (video.webkitEnterFullscreen) {
    try { video.webkitEnterFullscreen(); } catch { /* sin metadatos todavia */ }
  }
}

function syncFullscreenUi() {
  $("video-card").classList.toggle("is-fullscreen", isFullscreen());
  if (!isFullscreen()) unlockOrientation();
}

/** En vertical la tele se ve como una franja; en pantalla completa se pide
 *  apaisado. No todos los navegadores lo permiten (iOS no), y no pasa nada. */
function lockLandscape() {
  try { screen.orientation?.lock?.("landscape")?.catch?.(() => {}); } catch {}
}
function unlockOrientation() {
  try { screen.orientation?.unlock?.(); } catch {}
}

function teardownLocalVideo() {
  destroyHls();
  const video = $("video");
  try { video.pause(); } catch {}
  video.removeAttribute("src");
  try { video.load(); } catch {}
  state.streamUrl = null;
  state.streamChannelId = null;
  state.pendingPlay = false;
  state.audioPicked = false;
  renderAudioPicker(); // sin stream no hay pistas: esconde el botón
}
/* ─── Lista: filtrado, chips y render por lotes (40k canales sin bloquear) ─── */
/** Nombre legible del grupo: en Xtream `ch.group` es un id numérico ("142"). */
const groupLabel = (id) => (id ? (state.groupNames.get(id) || id) : "");

/** Los canales de TV no traen `kind` en el JSON porque "live" es el defecto. */
const kindOf = (ch) => ch.kind || "live";

/**
 * Texto por el que se busca cada canal, ya en minusculas y SIN ACENTOS, para
 * que "espana" encuentre "España" y "atres" encuentre "Atresmedia". Se calcula
 * una sola vez al cargar la lista: hacerlo en cada pulsacion con 40.000 canales
 * se notaria en un movil.
 */
function buildSearchIndex() {
  state.searchText = new Map(
    state.channels.map((ch) => [
      ch.id,
      fold(ch.name) + " " + fold(groupLabel(ch.group)) + " " + (ch.number ?? ""),
    ])
  );
}

function filteredChannels() {
  const q = fold(state.query);
  return state.channels.filter((ch) => {
    if (state.onlyFavs && !state.favIds.has(ch.id)) return false;
    if (state.kind && kindOf(ch) !== state.kind) return false;
    if (state.group && ch.group !== state.group) return false;
    // Busca por nombre, por categoria y por numero de dial; nunca por el id
    // interno, que al usuario no le dice nada.
    if (q && !(state.searchText.get(ch.id) || "").includes(q)) return false;
    return true;
  });
}

const KINDS = [
  { id: null, label: "Todo" },
  { id: "live", label: "TV en directo" },
  { id: "vod", label: "Películas" },
  { id: "series", label: "Series" },
];

/** Fila de tipos de contenido. Son cuatro, asi que caben siempre. */
function closeCategories() {
  state.catOpen = false;
  state.catQuery = "";
  const input = $("cat-search-input");
  if (input) input.value = "";
  renderCategories();
}

function renderKindChips() {
  const counts = new Map();
  for (const ch of state.channels) {
    const k = kindOf(ch);
    counts.set(k, (counts.get(k) || 0) + 1);
  }
  $("kind-chips").innerHTML = KINDS.map((k) => {
    const n = k.id === null ? state.channels.length : (counts.get(k.id) || 0);
    if (n === 0 && k.id !== null) return ""; // no ofrecer un filtro que no da nada
    const on = state.kind === k.id;
    return `<button type="button" class="chip${on ? " is-active" : ""}" role="tab"`
      + ` aria-selected="${on}" data-kind="${k.id ?? ""}">`
      + `${escapeHtml(k.label)} <small>${n.toLocaleString("es")}</small></button>`;
  }).join("");
}

/**
 * Categorias visibles: las del tipo elegido y que ademas casen con el buscador
 * del panel. Antes esto era una tira horizontal de ~290 chips con la barra de
 * scroll oculta por CSS, lo que la hacia inservible con raton.
 */
function visibleCategories() {
  const q = state.catQuery.trim().toLowerCase();
  return state.groups.filter((g) => {
    if (state.kind && (g.kind || "live") !== state.kind) return false;
    if (q && !g.name.toLowerCase().includes(q)) return false;
    return true;
  });
}

function renderCategories() {
  // Si el tipo elegido ya no contiene la categoria activa, se suelta el filtro
  // para no dejar la lista vacia sin explicacion.
  if (state.group && !state.groups.some((g) => g.id === state.group && (!state.kind || (g.kind || "live") === state.kind))) {
    state.group = null;
  }
  const chosen = state.groups.find((g) => g.id === state.group);
  $("cat-current").textContent = chosen ? chosen.name : "Todas las categorías";
  $("cat-clear").hidden = !state.group;
  $("cat-toggle").setAttribute("aria-expanded", String(state.catOpen));
  $("cat-panel").hidden = !state.catOpen;

  const cats = visibleCategories();
  const row = (id, name, count, active) =>
    `<button type="button" class="cat-item${active ? " is-active" : ""}" role="option"`
    + ` aria-selected="${active}" data-group="${escapeHtml(id ?? "")}">`
    + `<span class="cat-name">${escapeHtml(name)}</span>`
    + `<span class="cat-count">${count.toLocaleString("es")}</span></button>`;

  const total = state.kind
    ? cats.reduce((n, g) => n + g.count, 0)
    : state.channels.length;
  const head = state.catQuery.trim() ? "" : row(null, "Todas las categorías", total, !state.group);
  const body = cats.map((g) => row(g.id, g.name, g.count, g.id === state.group)).join("");
  $("cat-list").innerHTML = (head + body)
    || `<p class="cat-empty">Ninguna categoría coincide</p>`;
}

function rowHtml(ch, activeId) {
  const isFav = state.favIds.has(ch.id);
  const initials = (ch.name || "?").trim().slice(0, 2).toUpperCase();
  const logo = ch.logoUrl
    ? `<img src="${escapeHtml(ch.logoUrl)}" alt="" loading="lazy">`
    : `<span class="ch-fallback" aria-hidden="true">${escapeHtml(initials)}</span>`;
  // El número de dial que publica el proveedor: es el orden que el usuario espera.
  const num = ch.number != null
    ? `<span class="ch-num">${escapeHtml(String(ch.number))}</span>`
    : `<span class="ch-num is-empty" aria-hidden="true"></span>`;
  return `<div class="ch-row${ch.id === activeId ? " is-active" : ""}" role="listitem" tabindex="0" data-id="${escapeHtml(ch.id)}">`
    + num
    + `<span class="ch-logo">${logo}</span>`
    + `<span class="ch-info"><span class="ch-name">${escapeHtml(ch.name)}</span>`
    + `<span class="ch-group">${escapeHtml(groupLabel(ch.group))}</span></span>`
    + `<button type="button" class="ch-fav${isFav ? " is-fav" : ""}" title="Favorito" aria-label="Favorito" aria-pressed="${isFav}">`
    + `<svg viewBox="0 0 24 24"><path d="M12 20s-7.5-4.7-9.7-9C.7 7.9 2.4 4.5 5.8 4.5c2 0 3.4 1.1 4.2 2.4.8-1.3 2.2-2.4 4.2-2.4 3.4 0 5.1 3.4 3.5 6.5-2.2 4.3-9.7 9-9.7 9z"/></svg>`
    + `</button></div>`;
}

function renderList() {
  const list = filteredChannels();
  $("channels-count").textContent = list.length.toLocaleString("es");
  const activeId = state.now.channelId;
  $("channel-list").innerHTML = list.slice(0, state.visible).map((ch) => rowHtml(ch, activeId)).join("");
  $("channel-list").scrollTop = 0;
  $("empty-state").hidden = list.length > 0;
  $("list-sentinel").hidden = state.visible >= list.length;
}

function loadMore() {
  const list = filteredChannels();
  if (state.visible >= list.length) return;
  const activeId = state.now.channelId;
  const next = list.slice(state.visible, state.visible + state.BATCH);
  state.visible += state.BATCH;
  $("channel-list").insertAdjacentHTML("beforeend", next.map((ch) => rowHtml(ch, activeId)).join(""));
  $("list-sentinel").hidden = state.visible >= list.length;
}

function resetList() {
  state.visible = state.BATCH;
  renderList();
}

function highlightActiveRow(id) {
  document.querySelectorAll(".ch-row").forEach((r) => r.classList.toggle("is-active", r.dataset.id === id));
}

async function playChannel(id) {
  if (!isAdmin()) return; // el servidor responde 403; no intentarlo siquiera
  highlightActiveRow(id);
  goToPlayer();
  try { await apiPost("/api/channel/" + encodeURIComponent(id), "{}"); } catch {}
}

/* ─── Pestañas de movil ────────────────────────────────────────────────────
   En un telefono no caben reproductor y lista a la vez: buscando un canal solo
   se veian cuatro filas. Se separan en dos secciones y se cambia con la barra
   inferior, como en las apps de television. En escritorio no existe: alli van
   una al lado de la otra y la barra esta oculta por CSS. */

const isMobileLayout = () => window.matchMedia("(max-width: 760px)").matches;

function setTab(tab) {
  state.tab = tab;
  document.body.classList.toggle("tab-ver", tab === "ver");
  document.body.classList.toggle("tab-canales", tab === "canales");
  document.querySelectorAll(".mtab").forEach((b) =>
    b.setAttribute("aria-current", String(b.dataset.tab === tab)));
  // Al volver a la lista, el canal en curso a la vista.
  if (tab === "canales") scrollActiveRowIntoView();
}

function scrollActiveRowIntoView() {
  const row = document.querySelector(".ch-row.is-active");
  if (row) row.scrollIntoView({ block: "center" });
}

/** Tras elegir canal en movil se pasa a verlo, que es lo que se acaba de pedir. */
function goToPlayer() {
  if (isMobileLayout()) setTab("ver");
}

async function toggleFav(id, btn) {
  if (!isAdmin()) return;
  const desired = !state.favIds.has(id);
  desired ? state.favIds.add(id) : state.favIds.delete(id);
  if (btn) { btn.classList.toggle("is-fav", desired); btn.setAttribute("aria-pressed", String(desired)); }
  if (state.onlyFavs) renderList();
  try {
    await apiPost("/api/favorite/" + encodeURIComponent(id), { favorite: desired });
  } catch {
    desired ? state.favIds.delete(id) : state.favIds.add(id);
    if (btn) { btn.classList.toggle("is-fav", !desired); btn.setAttribute("aria-pressed", String(!desired)); }
  }
}

/* ─── Gestión de usuarios (solo administrador) ─── */
async function renderUsers() {
  const list = $("users-list");
  let users = [];
  try {
    const res = await api("/api/users");
    users = await res.json();
  } catch {
    list.innerHTML = "<p class='users-msg'>No se pudo cargar la lista de usuarios.</p>";
    return;
  }
  const admins = users.filter((u) => u.role === "admin").length;
  list.innerHTML = users.map((u) => {
    // El último administrador no se puede borrar: nadie podría volver a
    // gestionar cuentas sin editar el fichero de ajustes en el PC.
    const isLastAdmin = u.role === "admin" && admins <= 1;
    return `<div class="user-row">`
      + `<span class="user-info"><strong>${escapeHtml(u.username)}</strong>`
      + `<small>${u.role === "admin" ? "Administrador · control total" : "Invitado · solo ver"}</small></span>`
      + `<button type="button" class="btn btn-sm" data-pass="${escapeHtml(u.username)}">Contraseña</button>`
      + `<button type="button" class="btn btn-sm btn-danger" data-del="${escapeHtml(u.username)}"`
      + `${isLastAdmin ? " disabled title='Es el único administrador'" : ""}>Borrar</button>`
      + `</div>`;
  }).join("") || "<p class='users-msg'>No hay cuentas.</p>";

  list.querySelectorAll("[data-del]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const name = btn.dataset.del;
      if (!confirm(`¿Borrar la cuenta «${name}»?`)) return;
      const res = await fetch(`/api/users/${encodeURIComponent(name)}/delete`,
        { method: "POST", credentials: "include" });
      $("users-msg").textContent = res.ok
        ? `Cuenta «${name}» eliminada.`
        : "No se pudo borrar (¿es el único administrador?).";
      await renderUsers();
    });
  });
  list.querySelectorAll("[data-pass]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const name = btn.dataset.pass;
      const password = prompt(`Contraseña nueva para «${name}» (mín. 6 caracteres):`);
      if (!password) return;
      const res = await fetch(`/api/users/${encodeURIComponent(name)}/password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
        credentials: "include",
      });
      $("users-msg").textContent = res.ok
        ? `Contraseña de «${name}» cambiada (se ha cerrado su sesión).`
        : "La contraseña debe tener al menos 6 caracteres.";
    });
  });
}

/* ─── Volumen: relleno del slider ─── */
function updateVolumeFill() {
  const v = Number($("volume").value) || 0;
  $("volume").style.setProperty("--fill", v + "%");
}

/* ─── Parada total (al cerrar sesión / 401) ─── */
function stopEverything() {
  destroyHls();
  const video = $("video");
  try { video.pause(); } catch {}
  video.removeAttribute("src");
  try { video.load(); } catch {}
  state.streamUrl = null;
  state.streamChannelId = null;
  state.streamLoading = false;
  try { state.sse?.close(); } catch {}
  state.sse = null;
  setOnline(false);
}
/* ═══════════ Cableado de controles (una sola vez) ═══════════ */
function wireControls() {
  /* Login (o creación de la primera cuenta) */
  $("login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const username = $("user-input").value.trim();
    const password = $("pass-input").value;
    if (!username || !password) { showLogin("Rellena usuario y contraseña."); return; }
    $("login-submit").disabled = true;
    try {
      if (state.setupMode) await doSetup(username, password);
      else await doLogin(username, password);
    } finally {
      $("login-submit").disabled = false;
      $("pass-input").value = "";
    }
  });
  $("toggle-pass").addEventListener("click", () => {
    const inp = $("pass-input");
    const show = inp.type === "password";
    inp.type = show ? "text" : "password";
    $("toggle-pass").setAttribute("aria-label", show ? "Ocultar contraseña" : "Mostrar contraseña");
    inp.focus();
  });

  /* Cerrar sesión */
  $("btn-logout").addEventListener("click", async () => {
    try { await fetch("/logout", { method: "POST", credentials: "include" }); } catch {}
    state.token = null;
    showLogin("Sesión cerrada.");
  });

  /* Gestión de usuarios (solo administrador) */
  $("btn-users").addEventListener("click", async () => {
    await renderUsers();
    $("users-dialog").showModal();
  });
  $("users-close").addEventListener("click", () => $("users-dialog").close());
  $("new-user-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const username = $("nu-name").value.trim();
    const password = $("nu-pass").value;
    const role = $("nu-admin").checked ? "admin" : "viewer";
    const res = await fetch("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password, role }),
      credentials: "include",
    });
    if (res.ok) {
      $("nu-name").value = ""; $("nu-pass").value = ""; $("nu-admin").checked = false;
      $("users-msg").textContent = `Cuenta «${username}» creada.`;
      await renderUsers();
    } else {
      const err = await res.json().catch(() => ({}));
      $("users-msg").textContent = setupErrorText(err.error);
    }
  });

  /* Tema */
  document.querySelectorAll(".theme-opt").forEach((b) =>
    b.addEventListener("click", () => setTheme(b.dataset.themeOpt)));

  /* Pestañas de móvil */
  $("mtabs").addEventListener("click", (e) => {
    const b = e.target.closest(".mtab");
    if (b) setTab(b.dataset.tab);
  });

  /* Búsqueda + filtro de favoritos */
  const onSearch = debounce(() => {
    state.query = $("search-input").value;
    resetList();
  }, 180);
  const reflectSearch = (value) => {
    $("search-clear").hidden = value === "";
    document.body.classList.toggle("searching", value !== "");
  };
  $("search-input").addEventListener("input", (e) => {
    reflectSearch(e.target.value);
    // Buscando se quiere ver la lista, no el vídeo: en móvil se salta sola.
    if (e.target.value !== "" && isMobileLayout()) setTab("canales");
    onSearch();
  });
  $("search-clear").addEventListener("click", () => {
    const input = $("search-input");
    input.value = "";
    reflectSearch("");
    state.query = "";
    resetList();
    input.focus();
  });
  $("fav-filter").addEventListener("click", (e) => {
    state.onlyFavs = !state.onlyFavs;
    e.currentTarget.setAttribute("aria-pressed", String(state.onlyFavs));
    e.currentTarget.classList.toggle("is-active", state.onlyFavs);
    resetList();
  });

  /* Pista de audio */
  $("audio-toggle").addEventListener("click", (e) => {
    e.stopPropagation();
    const wrap = $("audio-picker");
    const open = wrap.classList.toggle("is-open");
    $("audio-toggle").setAttribute("aria-expanded", String(open));
  });
  $("audio-menu").addEventListener("click", (e) => {
    const opt = e.target.closest(".audio-opt");
    if (!opt) return;
    setAudioTrack(Number(opt.dataset.track));
    $("audio-picker").classList.remove("is-open");
    $("audio-toggle").setAttribute("aria-expanded", "false");
  });
  document.addEventListener("click", (e) => {
    if (e.target.closest("#audio-picker")) return;
    $("audio-picker").classList.remove("is-open");
    $("audio-toggle").setAttribute("aria-expanded", "false");
  });

  /* Tipo de contenido (delegación) */
  $("kind-chips").addEventListener("click", (e) => {
    const chipEl = e.target.closest(".chip");
    if (!chipEl) return;
    state.kind = chipEl.dataset.kind || null;
    renderKindChips();
    renderCategories(); // el tipo acota qué categorías tienen sentido
    resetList();
  });

  /* Categorías: abrir/cerrar el panel, buscar dentro y elegir */
  $("cat-toggle").addEventListener("click", () => {
    state.catOpen = !state.catOpen;
    renderCategories();
    if (state.catOpen) $("cat-search-input").focus();
  });

  $("cat-clear").addEventListener("click", () => {
    state.group = null;
    renderCategories();
    resetList();
  });

  $("cat-search-input").addEventListener("input", (e) => {
    state.catQuery = e.target.value;
    renderCategories();
  });

  $("cat-search-input").addEventListener("keydown", (e) => {
    if (e.key === "Escape") { closeCategories(); $("cat-toggle").focus(); }
  });

  $("cat-list").addEventListener("click", (e) => {
    const item = e.target.closest(".cat-item");
    if (!item) return;
    state.group = item.dataset.group || null;
    closeCategories();
    resetList();
  });

  // Clic fuera del panel: cerrarlo, como cualquier desplegable.
  document.addEventListener("click", (e) => {
    if (!state.catOpen) return;
    if (e.target.closest("#cat-panel") || e.target.closest("#cat-toggle")) return;
    closeCategories();
  });

  /* Lista de canales (delegación: play + favorito + teclado) */
  const list = $("channel-list");
  list.addEventListener("click", (e) => {
    const fav = e.target.closest(".ch-fav");
    if (fav) { toggleFav(fav.closest(".ch-row").dataset.id, fav); return; }
    const row = e.target.closest(".ch-row");
    if (row) playChannel(row.dataset.id);
  });
  list.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const row = e.target.closest(".ch-row");
    if (row) { e.preventDefault(); playChannel(row.dataset.id); }
  });

  /* Scroll infinito */
  if ("IntersectionObserver" in window) {
    new IntersectionObserver((entries) => {
      if (entries.some((en) => en.isIntersecting)) loadMore();
    }, { root: list, rootMargin: "400px" }).observe($("list-sentinel"));
  } else {
    list.addEventListener("scroll", () => {
      if (list.scrollTop + list.clientHeight >= list.scrollHeight - 300) loadMore();
    });
  }

  /* Controles de reproducción (escritorio es la fuente de verdad) */
  const cmd = (path, body) => () => apiPost(path, body).catch(() => {});
  $("btn-prev").addEventListener("click", cmd("/api/player/prev", {}));
  $("btn-next").addEventListener("click", cmd("/api/player/next", {}));
  $("btn-stop").addEventListener("click", cmd("/api/player/stop", {}));
  $("btn-playpause").addEventListener("click", cmd("/api/player/playpause", {}));
  $("btn-mute").addEventListener("click", () => {
    const muted = !state.now.isMuted;
    const video = $("video");
    video.muted = muted;
    apiPost("/api/player/mute", { muted }).catch(() => {});
  });

  /* Volumen: relleno + volumen local en vivo, POST al soltar */
  const vol = $("volume");
  vol.addEventListener("input", () => {
    updateVolumeFill();
    $("video").volume = (Number(vol.value) || 0) / 100;
  });
  vol.addEventListener("pointerdown", () => { volumeDrag = true; });
  window.addEventListener("pointerup", () => { if (volumeDrag) { volumeDrag = false; } });
  vol.addEventListener("change", () => {
    const v = Math.round(Number(vol.value) || 0);
    $("video").volume = v / 100;
    apiPost("/api/player/volume", { volume: v }).catch(() => {});
  });

  /* Pantalla completa */
  $("btn-fs").addEventListener("click", toggleFullscreen);
  ["fullscreenchange", "webkitfullscreenchange"].forEach((ev) =>
    document.addEventListener(ev, syncFullscreenUi));
  // iOS avisa por el <video>, no por el documento.
  $("video").addEventListener("webkitbeginfullscreen", () => { state.iosFullscreen = true; syncFullscreenUi(); });
  $("video").addEventListener("webkitendfullscreen", () => { state.iosFullscreen = false; syncFullscreenUi(); });

  /* Overlays: reintentar y big-play */
  $("btn-retry").addEventListener("click", () => {
    state.retryCount = 0;
    state.localError = null;
    startStream();
  });
  $("big-play").addEventListener("click", () => {
    $("big-play").hidden = true;
    $("video").play().catch(() => { $("big-play").hidden = false; });
  });

  /* Eventos del <video>: TODOS pasan por renderOverlays(), que es el unico que
     decide que capa se ve. Antes cada evento tocaba los overlays por su cuenta y
     el circulo de "Sintonizando" se quedaba clavado sobre la imagen. */
  const video = $("video");
  ["playing", "canplay", "timeupdate"].forEach((ev) =>
    video.addEventListener(ev, () => {
      state.streamLoading = false;
      renderOverlays();
    }));
  ["waiting", "stalled", "pause", "emptied"].forEach((ev) =>
    video.addEventListener(ev, renderOverlays));

  /* Aviso de audio: si es por autoplay bloqueado, un toque lo activa */
  $("audio-notice").addEventListener("click", (e) => {
    if (e.currentTarget.dataset.action !== "unmute") return;
    const v = $("video");
    v.muted = false;
    v.volume = (Number($("volume").value) || 80) / 100;
    hideAudioNotice();
    setMuteIcon(false);
    scheduleAudioCheck(); // por si además el códec no es reproducible
  });
  video.addEventListener("error", () => {
    if (!state.streamUrl) return;
    showStreamError("El stream no responde. Reintentando…");
    scheduleRetry();
  });

  /* Atajos de teclado */
  document.addEventListener("keydown", (e) => {
    const typing = e.target.closest("input, textarea, select");
    if (e.key === "/" && !typing) { e.preventDefault(); $("search-input").focus(); return; }
    if (typing) return;
    switch (e.key.toLowerCase()) {
      case " ": e.preventDefault(); cmd("/api/player/playpause", {})(); break;
      case "m": $("btn-mute").click(); break;
      case "f": $("btn-fs").click(); break;
      case "n": cmd("/api/player/next", {})(); break;
      case "p": cmd("/api/player/prev", {})(); break;
      case "escape": if (isFullscreen()) toggleFullscreen(); break;
    }
  });
}

/* ═══════════ Arranque ═══════════ */
(async function boot() {
  setTheme(localStorage.getItem("web_theme") || "auto");
  // Restos del sistema anterior de token en localStorage: ya no se usa (la sesion
  // va en cookie), y dejarlo ahi solo seria una credencial vieja al aire.
  try { localStorage.removeItem("iptv_token"); } catch {}
  // La URL ya no debe llevar credenciales; si viene una de un enlace antiguo, se
  // limpia para no dejarla en el historial ni en la barra de direcciones.
  if (location.search) history.replaceState(null, "", location.pathname);

  wireControls();
  setTab("ver"); // en escritorio las clases no hacen nada: la barra esta oculta

  // ¿Hay que crear la primera cuenta, o ya se puede iniciar sesion?
  let info = { needsSetup: false, session: null };
  try {
    info = await (await fetch("/api/auth", { credentials: "include" })).json();
  } catch {
    showLogin("No se pudo conectar con la app de escritorio.");
    return;
  }
  setSetupMode(Boolean(info.needsSetup));

  // Si el navegador ya tenia sesion valida, entrar directo.
  if (info.session && await tryResumeSession()) return;
  showLogin();
})();
