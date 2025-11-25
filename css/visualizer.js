document.addEventListener('DOMContentLoaded', () => {
  /**
   * Crea un reproductor de radio personalizado con visualizador y metadatos.
   * @param {object} config - La configuración para el reproductor.
   */
  function createCustomPlayer(config) {
    const audioPlayer = document.getElementById(config.audioPlayerId);
    const playPauseBtn = document.getElementById(config.playPauseBtnId);
    const visualizerCanvas = document.getElementById(config.visualizerId);
    const volumeSlider = document.getElementById(config.volumeSliderId);
    const albumArt = document.getElementById(config.albumArtId);
    const songTitleElement = document.getElementById(config.songTitleId);

    if (!audioPlayer || !playPauseBtn || !visualizerCanvas || !volumeSlider || !albumArt || !songTitleElement) {
      console.error(`Faltan elementos para el reproductor: ${config.audioPlayerId}`);
      return;
    }

    // --- Comprobación de compatibilidad de formato (para AAC) ---
    if (config.format === 'aac') {
      const canPlayAAC = !!audioPlayer.canPlayType('audio/aac').replace(/no/, '');
      if (!canPlayAAC) {
        playPauseBtn.disabled = true;
        songTitleElement.textContent = 'Formato no compatible en este navegador.';
        console.warn('Este navegador no soporta la reproducción de AAC.');
        return;
      }
    }

    const iconPlay = playPauseBtn.querySelector('.icon-play');
    const iconPause = playPauseBtn.querySelector('.icon-pause');
    let audioContext;
    let analyser;
    let source;
    let dataArray;
    let lastSongTitle = '';
    let metadataInterval = null;

    function setupAudioContext() {
      if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
        analyser = audioContext.createAnalyser();
        source = audioContext.createMediaElementSource(audioPlayer);
        source.connect(analyser);
        analyser.connect(audioContext.destination);
        analyser.fftSize = 64;
        dataArray = new Uint8Array(analyser.frequencyBinCount);
      }
    }

    volumeSlider.addEventListener('input', (e) => {
      audioPlayer.volume = e.target.value / 100;
    });

    playPauseBtn.addEventListener('click', () => {
      if (audioContext && audioContext.state === 'suspended') {
        audioContext.resume();
      }
      if (audioPlayer.paused) {
        setupAudioContext();
        audioPlayer.play().catch(error => console.error("Error al reproducir:", error));
      } else {
        audioPlayer.pause();
      }
    });

    audioPlayer.addEventListener('play', () => {
      iconPlay.style.display = 'none';
      iconPause.style.display = 'inline-block';
      drawVisualizer();
      if (!lastSongTitle) songTitleElement.textContent = 'Cargando información...';
      startMetadataFetching();
    });

    audioPlayer.addEventListener('pause', () => {
      iconPlay.style.display = 'inline-block';
      iconPause.style.display = 'none';
      stopMetadataFetching();
    });

    function startMetadataFetching() {
      if (metadataInterval) return;
      fetchMetadata(); // Llama inmediatamente la primera vez
      metadataInterval = setInterval(fetchMetadata, config.metadataIntervalMs || 5000);
    }

    function stopMetadataFetching() {
      clearInterval(metadataInterval);
      metadataInterval = null;
    }

    async function fetchMetadata() {
      try {
        const response = await fetch(config.metadataUrl);
        const data = await response.json();
        const songTitle = (data[config.metadataSongProperty] || '').trim();

        if (songTitle && songTitle !== lastSongTitle) {
          lastSongTitle = songTitle;
          songTitleElement.textContent = songTitle;
          fetchAlbumArt(songTitle);
        }
      } catch (error) {
        console.error('Error al obtener metadatos:', error);
      }
    }

    async function fetchAlbumArt(songTitle) {
      try {
        const searchTerm = songTitle.replace(/-/g, ' ').replace(/\s+/g, ' ').trim();
        const itunesUrl = `https://itunes.apple.com/search?term=${encodeURIComponent(searchTerm)}&entity=song&limit=1`;
        const response = await fetch(itunesUrl);
        const data = await response.json();

        if (data.resultCount > 0 && data.results[0].artworkUrl100) {
          albumArt.src = data.results[0].artworkUrl100.replace('100x100', '600x600');
        } else {
          albumArt.src = config.defaultAlbumArt;
        }
      } catch (error) {
        console.error('Error al obtener carátula:', error);
        albumArt.src = config.defaultAlbumArt;
      }
    }

    function drawVisualizer() {
      const ctx = visualizerCanvas.getContext('2d');

      function draw() {
        if (!audioPlayer || audioPlayer.paused) {
          ctx.clearRect(0, 0, visualizerCanvas.width, visualizerCanvas.height);
          return;
        }
        requestAnimationFrame(draw);
        analyser.getByteFrequencyData(dataArray);
        ctx.clearRect(0, 0, visualizerCanvas.width, visualizerCanvas.height);
        const barWidth = visualizerCanvas.width / analyser.frequencyBinCount;

        for (let i = 0; i < analyser.frequencyBinCount; i++) {
          const barHeight = (dataArray[i] / 255) * visualizerCanvas.height;
          ctx.fillStyle = config.visualizerColor;
          ctx.fillRect(i * barWidth, visualizerCanvas.height - barHeight, barWidth - 1, barHeight);
        }
      }
      draw();
    }
  }

  // --- LÓGICA PARA RADIO TRANSPARENCIA (Usa EventSource/SSE) ---
  const audioPlayerTransparencia = document.getElementById('audio-player-transparencia');
  const playPauseBtnTransparencia = document.getElementById('play-pause-btn-transparencia');
  if (audioPlayerTransparencia && playPauseBtnTransparencia) {
    const visualizerTransparencia = document.getElementById('visualizer-transparencia');
    const volumeSliderTransparencia = document.getElementById('volume-slider');
    const albumArtTransparencia = document.getElementById('album-art');
    const songTitleElementTransparencia = document.getElementById('song-title');
    const defaultAlbumArtTransparencia = 'img/logo.png';
    const iconPlay = playPauseBtnTransparencia.querySelector('.icon-play');
    const iconPause = playPauseBtnTransparencia.querySelector('.icon-pause');
    let audioContext, analyser, source, dataArray, lastSongTitle = '', metadataEventSource = null;

    function setupAudioContextTransparencia() {
      if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
        analyser = audioContext.createAnalyser();
        source = audioContext.createMediaElementSource(audioPlayerTransparencia);
        source.connect(analyser);
        analyser.connect(audioContext.destination);
        analyser.fftSize = 64;
        dataArray = new Uint8Array(analyser.frequencyBinCount);
      }
    }

    volumeSliderTransparencia.addEventListener('input', (e) => {
      audioPlayerTransparencia.volume = e.target.value / 100;
    });

    playPauseBtnTransparencia.addEventListener('click', () => {
      if (audioContext && audioContext.state === 'suspended') audioContext.resume();
      if (audioPlayerTransparencia.paused) {
        setupAudioContextTransparencia();
        audioPlayerTransparencia.play().catch(e => console.error("Error al reproducir Transparencia:", e));
      } else {
        audioPlayerTransparencia.pause();
      }
    });

    audioPlayerTransparencia.addEventListener('play', () => {
      iconPlay.style.display = 'none';
      iconPause.style.display = 'inline-block';
      drawRealVisualizer();
      if (!lastSongTitle) songTitleElementTransparencia.textContent = 'Cargando información...';
      if (!metadataEventSource) subscribeToMetadata();
    });

    audioPlayerTransparencia.addEventListener('pause', () => {
      iconPlay.style.display = 'inline-block';
      iconPause.style.display = 'none';
    });

    function subscribeToMetadata() {
      const metadataUrl = 'https://api.zeno.fm/mounts/metadata/subscribe/a5tnl0xjodbvv/';
      metadataEventSource = new EventSource(metadataUrl);
      metadataEventSource.onmessage = async (event) => {
        try {
          const streamData = JSON.parse(event.data);
          const songTitle = (streamData.streamTitle || '').trim();
          if (songTitle && songTitle !== lastSongTitle) {
            lastSongTitle = songTitle;
            songTitleElementTransparencia.textContent = songTitle;
            fetchAlbumArtTransparencia(songTitle);
          }
        } catch (error) {
          console.error('Error al procesar metadatos de Transparencia:', error);
        }
      };
      metadataEventSource.onerror = (error) => {
        console.error('Error en EventSource de Transparencia:', error);
      };
    }

    async function fetchAlbumArtTransparencia(songTitle) {
      // Esta función es la misma que la de createCustomPlayer, se puede reutilizar
      try {
        const searchTerm = songTitle.replace(/-/g, ' ').replace(/\s+/g, ' ').trim();
        const itunesUrl = `https://itunes.apple.com/search?term=${encodeURIComponent(searchTerm)}&entity=song&limit=1`;
        const response = await fetch(itunesUrl);
        const data = await response.json();
        if (data.resultCount > 0 && data.results[0].artworkUrl100) {
          albumArtTransparencia.src = data.results[0].artworkUrl100.replace('100x100', '600x600');
        } else {
          albumArtTransparencia.src = defaultAlbumArtTransparencia;
        }
      } catch (error) {
        albumArtTransparencia.src = defaultAlbumArtTransparencia;
      }
    }

    function drawRealVisualizer() {
      const ctx = visualizerTransparencia.getContext('2d');
      function draw() {
        if (!audioPlayerTransparencia || audioPlayerTransparencia.paused) { ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height); return; }
        requestAnimationFrame(draw);
        analyser.getByteFrequencyData(dataArray);
        ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
        const barWidth = ctx.canvas.width / analyser.frequencyBinCount;
        for (let i = 0; i < analyser.frequencyBinCount; i++) {
          const barHeight = (dataArray[i] / 255) * ctx.canvas.height;
          ctx.fillStyle = '#0077cc';
          ctx.fillRect(i * barWidth, ctx.canvas.height - barHeight, barWidth - 1, barHeight);
        }
      }
      draw();
    }
  }

  // --- Configuración para Extasis Radio (RCAST Polling) ---
  createCustomPlayer({
    audioPlayerId: 'audio-player-extasis',
    playPauseBtnId: 'play-pause-btn-extasis',
    visualizerId: 'visualizer-extasis',
    volumeSliderId: 'volume-slider-extasis',
    albumArtId: 'album-art-extasis',
    songTitleId: 'song-title-extasis',
    defaultAlbumArt: 'img/exlogo.png',
    visualizerColor: '#e4002b',
    format: 'aac', // Especificamos el formato para la comprobación
    metadataUrl: 'https://players.rcast.net/stats/72898',
    metadataSongProperty: 'song', // La propiedad que contiene el título
    metadataIntervalMs: 4000 // Consultar cada 4 segundos
  });
});