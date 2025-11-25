document.addEventListener('DOMContentLoaded', () => {
  /**
   * Configura un reproductor de radio personalizado para una estación de Zeno.fm.
   * @param {object} config - La configuración para el reproductor.
   */
  function setupRadioPlayer(config) {
    const audioPlayer = document.getElementById(config.audioPlayerId);
    const playPauseBtn = document.getElementById(config.playPauseBtnId);
    const visualizerCanvas = document.getElementById(config.visualizerId);
    const volumeSlider = document.getElementById(config.volumeSliderId);
    const albumArt = document.getElementById(config.albumArtId);
    const songTitleElement = document.getElementById(config.songTitleId);

    const iconPlay = playPauseBtn.querySelector('.icon-play');
    const iconPause = playPauseBtn.querySelector('.icon-pause');
    let audioContext;
    let analyser;
    let source;
    let dataArray;
    let lastSongTitle = '';
    let metadataEventSource = null;

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

    const volumeIcon = volumeSlider.parentElement.querySelector('.volume-icon');
    const volumeIconMuted = volumeSlider.parentElement.querySelector('.volume-icon-muted');

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

    volumeIcon.addEventListener('click', () => toggleMute());
    volumeIconMuted.addEventListener('click', () => toggleMute());

    function toggleMute() {
      audioPlayer.muted = !audioPlayer.muted;
      updateMuteIcon();
    }

    audioPlayer.addEventListener('play', () => {
      iconPlay.style.display = 'none';
      iconPause.style.display = 'inline-block';
      drawRealVisualizer();
      if (!lastSongTitle) songTitleElement.textContent = 'Cargando información...';
      if (!metadataEventSource) subscribeToMetadata();
    });

    audioPlayer.addEventListener('pause', () => {
      iconPlay.style.display = 'inline-block';
      iconPause.style.display = 'none';
    });

    audioPlayer.addEventListener('volumechange', () => {
      updateMuteIcon();
    });

    function subscribeToMetadata() {
      metadataEventSource = new EventSource(config.metadataUrl);
      metadataEventSource.onmessage = async (event) => {
        try {
          const streamData = JSON.parse(event.data);
          const songTitle = (streamData.streamTitle || '').trim();
          if (songTitle && songTitle !== lastSongTitle) {
            lastSongTitle = songTitle;
            songTitleElement.textContent = songTitle;
            fetchAlbumArt(songTitle);
          }
        } catch (error) {
          console.error(`Error al procesar metadatos de ${config.audioPlayerId}:`, error);
        }
      };
      metadataEventSource.onerror = (error) => {
        console.error(`Error en EventSource de ${config.audioPlayerId}:`, error);
      };
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

    function drawRealVisualizer() {
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

    function updateMuteIcon() {
      if (audioPlayer.muted || audioPlayer.volume === 0) {
        volumeIcon.style.display = 'none';
        volumeIconMuted.style.display = 'inline';
        if (audioPlayer.volume > 0) volumeSlider.value = 0;
      } else {
        volumeIcon.style.display = 'inline';
        volumeIconMuted.style.display = 'none';
        volumeSlider.value = audioPlayer.volume * 100;
      }
    }
  }

  // --- Configuración para Radio Transparencia ---
  setupRadioPlayer({
    audioPlayerId: 'audio-player-transparencia',
    playPauseBtnId: 'play-pause-btn-transparencia',
    visualizerId: 'visualizer-transparencia',
    volumeSliderId: 'volume-slider',
    albumArtId: 'album-art',
    songTitleId: 'song-title',
    defaultAlbumArt: 'img/logo.png',
    visualizerColor: '#0077cc',
    metadataUrl: 'https://api.zeno.fm/mounts/metadata/subscribe/a5tnl0xjodbvv/',
  });

  // --- Configuración para Extasis Radio ---
  setupRadioPlayer({
    audioPlayerId: 'audio-player-extasis',
    playPauseBtnId: 'play-pause-btn-extasis',
    visualizerId: 'visualizer-extasis',
    volumeSliderId: 'volume-slider-extasis',
    albumArtId: 'album-art-extasis',
    songTitleId: 'song-title-extasis',
    defaultAlbumArt: 'img/exlogo.png',
    visualizerColor: '#e4002b',
    metadataUrl: 'https://api.zeno.fm/mounts/metadata/subscribe/xfwg9mmhrd0uv/',
  });
});