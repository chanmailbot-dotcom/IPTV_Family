(function () {
  "use strict";

  var state = { categories: [], channels: [], favoriteIds: [], activeCategory: "all", search: "", currentChannelId: null };
  var hls = null;

  var loginScreen = document.getElementById("login-screen");
  var appScreen = document.getElementById("app-screen");
  var tokenInput = document.getElementById("token-input");
  var loginBtn = document.getElementById("login-btn");
  var loginError = document.getElementById("login-error");
  var playlistName = document.getElementById("playlist-name");
  var searchInput = document.getElementById("search");
  var categoriesEl = document.getElementById("categories");
  var channelsEl = document.getElementById("channels");
  var video = document.getElementById("player");
  var nowPlayingName = document.getElementById("now-playing-name");
  var nowPlayingStatus = document.getElementById("now-playing-status");

  function showLogin(message) {
    loginScreen.classList.remove("hidden");
    appScreen.classList.add("hidden");
    loginError.textContent = message || "";
  }

  function showApp() {
    loginScreen.classList.add("hidden");
    appScreen.classList.remove("hidden");
  }

  function loginWithToken(token) {
    return fetch("/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: token }),
    });
  }

  loginBtn.addEventListener("click", function () {
    var token = tokenInput.value.trim();
    if (!token) return;
    loginWithToken(token).then(function (res) {
      if (res.ok) {
        init();
      } else {
        showLogin("Token incorrecto.");
      }
    }).catch(function () { showLogin("No se pudo conectar."); });
  });

  // Si se abre con ?token=... en la URL (enlace copiado de Ajustes), entra solo,
  // sin que haga falta teclear nada. Se limpia de la URL despues por si se comparte
  // la pantalla o se guarda como marcador.
  function tryAutoLoginFromUrl() {
    var params = new URLSearchParams(window.location.search);
    var token = params.get("token");
    if (!token) return Promise.resolve(false);
    return loginWithToken(token).then(function (res) {
      history.replaceState(null, "", window.location.pathname);
      return res.ok;
    }).catch(function () { return false; });
  }

  function renderCategories() {
    var html = '<button data-id="all" class="' + (state.activeCategory === "all" ? "active" : "") + '">Todas</button>';
    state.categories.forEach(function (c) {
      html += '<button data-id="' + c.id + '" class="' + (state.activeCategory === c.id ? "active" : "") + '">' + escapeHtml(c.name) + "</button>";
    });
    categoriesEl.innerHTML = html;
    Array.prototype.forEach.call(categoriesEl.querySelectorAll("button"), function (btn) {
      btn.addEventListener("click", function () {
        state.activeCategory = btn.getAttribute("data-id");
        renderCategories();
        renderChannels();
      });
    });
  }

  function renderChannels() {
    var term = state.search.toLowerCase();
    var list = state.channels.filter(function (ch) {
      var inCategory = state.activeCategory === "all" || ch.group === state.activeCategory;
      var matches = !term || ch.name.toLowerCase().indexOf(term) >= 0;
      return inCategory && matches;
    });
    channelsEl.innerHTML = list.map(function (ch) {
      var isFav = state.favoriteIds.indexOf(ch.id) >= 0;
      var logo = ch.logoUrl ? '<img src="' + escapeAttr(ch.logoUrl) + '" alt="" onerror="this.style.display=\'none\'" />' : "";
      return '<li data-id="' + ch.id + '">' + logo +
        '<span class="name">' + escapeHtml(ch.name) + "</span>" +
        '<span class="fav' + (isFav ? " active" : "") + '" data-fav="' + ch.id + '">★</span></li>';
    }).join("");

    Array.prototype.forEach.call(channelsEl.querySelectorAll("li"), function (li) {
      li.addEventListener("click", function (ev) {
        if (ev.target.hasAttribute("data-fav")) return;
        playChannel(li.getAttribute("data-id"));
      });
    });
    Array.prototype.forEach.call(channelsEl.querySelectorAll("[data-fav]"), function (star) {
      star.addEventListener("click", function (ev) {
        ev.stopPropagation();
        var id = star.getAttribute("data-fav");
        var makeFav = state.favoriteIds.indexOf(id) < 0;
        fetch("/api/favorite/" + id, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ favorite: makeFav }),
        }).then(function () {
          if (makeFav) state.favoriteIds.push(id);
          else state.favoriteIds = state.favoriteIds.filter(function (x) { return x !== id; });
          renderChannels();
        });
      });
    });
  }

  function playChannel(id) {
    fetch("/api/channel/" + id, { method: "POST" });
  }

  function applyNowPlaying(np) {
    nowPlayingName.textContent = np.channelName || "Sin reproducir";
    nowPlayingStatus.textContent = np.error ? ("Error: " + np.error) : (np.isBuffering ? "Cargando…" : (np.isPlaying ? "En directo" : ""));
    Array.prototype.forEach.call(channelsEl.querySelectorAll("li"), function (li) {
      li.classList.toggle("playing", li.getAttribute("data-id") === np.channelId);
    });
    // Solo recargar el <video> cuando el canal cambia de verdad: si no, cada aviso de
    // "cargando" -> "en directo" del mismo canal reinicia el reproductor otra vez y
    // parece que se queda pensando para siempre.
    if (np.channelId && np.channelId !== state.currentChannelId) {
      state.currentChannelId = np.channelId;
      loadPlayer();
    } else if (!np.channelId) {
      state.currentChannelId = null;
    }
  }

  function loadPlayer() {
    var url = "/stream/current.m3u8";
    if (window.Hls && window.Hls.isSupported()) {
      if (hls) hls.destroy();
      hls = new window.Hls();
      hls.loadSource(url);
      hls.attachMedia(video);
      video.play().catch(function () {});
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = url;
      video.play().catch(function () {});
    }
  }

  function connectEvents() {
    var source = new EventSource("/api/events");
    source.addEventListener("now-playing", function (ev) {
      applyNowPlaying(JSON.parse(ev.data));
    });
    source.addEventListener("channels", function () {
      loadState();
    });
    source.onerror = function () {
      source.close();
      setTimeout(connectEvents, 3000);
    };
  }

  function loadState() {
    fetch("/api/state").then(function (res) {
      if (res.status === 401) { showLogin(); throw new Error("unauthorized"); }
      return res.json();
    }).then(function (data) {
      state.categories = data.categories || [];
      state.channels = data.channels || [];
      state.favoriteIds = data.favoriteChannelIds || [];
      playlistName.textContent = data.playlistName || "IPTV Family";
      renderCategories();
      renderChannels();
      applyNowPlaying(data.nowPlaying || {});
    }).catch(function () {});
  }

  searchInput.addEventListener("input", function () {
    state.search = searchInput.value;
    renderChannels();
  });

  function escapeHtml(text) {
    return String(text).replace(/[&<>"]/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c];
    });
  }
  function escapeAttr(text) { return escapeHtml(text); }

  function init() {
    showApp();
    loadState();
    connectEvents();
  }

  function boot() {
    fetch("/api/state").then(function (res) {
      if (res.ok) { init(); return; }
      tryAutoLoginFromUrl().then(function (loggedIn) {
        if (loggedIn) init();
        else showLogin();
      });
    }).catch(function () { showLogin(); });
  }

  boot();
})();
